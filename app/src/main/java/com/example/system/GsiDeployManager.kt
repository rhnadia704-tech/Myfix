package com.example.system

import android.content.Context
import com.example.model.DiagnosticLogLine
import com.example.model.LogSeverity
import com.example.model.LogTag
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GsiDeployManager(private val context: Context) {

    data class DeployOutcome(
        val success: Boolean,
        val logs: List<DiagnosticLogLine>,
        val summary: String,
    )

    suspend fun deployToSystem(): DeployOutcome = withContext(Dispatchers.IO) {
        val logs = mutableListOf<DiagnosticLogLine>()
        fun addLog(tag: LogTag, msg: String, sev: LogSeverity = LogSeverity.INFO) {
            logs += HalInspector.nextLine(tag, msg, sev)
        }

        val activeLib = BinaryManager.resolveActiveLibraryFile(context)
        if (activeLib == null) {
            addLog(
                LogTag.ERR,
                "Aucun libfodfix.so disponible. Importez un binaire via Importer ou compilez avec NDK.",
                LogSeverity.ERROR,
            )
            return@withContext DeployOutcome(
                success = false,
                logs = logs,
                summary = "Aucun binaire libfodfix.so disponible.",
            )
        }

        val elfInfo = ElfInspector.inspect(activeLib)
        if (!elfInfo.isValidArm64Elf) {
            addLog(LogTag.ERR, elfInfo.summary, LogSeverity.ERROR)
            return@withContext DeployOutcome(false, logs, elfInfo.summary)
        }
        addLog(LogTag.CORE, "${elfInfo.summary} · SHA256=${elfInfo.sha256.take(12)}…", LogSeverity.SUCCESS)

        if (!RootShellExecutor.isRootAvailable()) {
            addLog(LogTag.ERR, "Droits root (su) indisponibles. Utilisez Export in sdcard.", LogSeverity.ERROR)
            return@withContext DeployOutcome(
                success = false,
                logs = logs,
                summary = "Root (su) requis pour écrire dans /system/lib64.",
            )
        }
        addLog(LogTag.SYS, "Accès root (su) confirmé", LogSeverity.SUCCESS)

        val stageDir = File(context.cacheDir, "myfix_stage").apply {
            deleteRecursively()
            mkdirs()
        }
        val assetFiles = listOf(
            "myfix/init/fodfix.rc" to "fodfix.rc",
            "myfix/selinux/fodfix.te" to "fodfix.te",
            "myfix/selinux/fodfix_file_contexts" to "fodfix_file_contexts",
            "myfix/selinux/plat_sepolicy_fodfix.cil" to "plat_sepolicy_fodfix.cil",
        )
        for ((assetPath, destName) in assetFiles) {
            context.assets.open(assetPath).use { input ->
                File(stageDir, destName).outputStream().use { output -> input.copyTo(output) }
            }
        }

        val remountCmd =
            "mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null"
        val remountResult = RootShellExecutor.exec(remountCmd, asRoot = true, timeoutMs = 6_000L)
        if (!remountResult.isSuccess) {
            stageDir.deleteRecursively()
            addLog(
                LogTag.ERR,
                "Impossible de remonter /system en écriture (EROFS ou shared_blocks). Utilisez Export in sdcard.",
                LogSeverity.ERROR,
            )
            return@withContext DeployOutcome(
                success = false,
                logs = logs,
                summary = "Partition /system en lecture seule. Exportez sur sdcard.",
            )
        }
        addLog(LogTag.SYS, "Partition /system remontée en lecture-écriture", LogSeverity.SUCCESS)

        val deployCmd = buildString {
            append("mkdir -p /system/lib64 /system/etc/init /system/etc/selinux/myfix && ")
            append("cp -f '${activeLib.absolutePath}' /system/lib64/libfodfix.so && ")
            append("chmod 0644 /system/lib64/libfodfix.so && ")
            append("(chcon u:object_r:system_lib_file:s0 /system/lib64/libfodfix.so 2>/dev/null || true) && ")
            append("cp -f '${File(stageDir, "fodfix.rc").absolutePath}' /system/etc/init/fodfix.rc && ")
            append("chmod 0644 /system/etc/init/fodfix.rc && ")
            append("(chcon u:object_r:system_file:s0 /system/etc/init/fodfix.rc 2>/dev/null || true) && ")
            append("cp -f '${File(stageDir, "fodfix.te").absolutePath}' /system/etc/selinux/myfix/fodfix.te && ")
            append("cp -f '${File(stageDir, "fodfix_file_contexts").absolutePath}' /system/etc/selinux/myfix/fodfix_file_contexts && ")
            append("cp -f '${File(stageDir, "plat_sepolicy_fodfix.cil").absolutePath}' /system/etc/selinux/myfix/plat_sepolicy_fodfix.cil && ")
            append("chmod 0644 /system/etc/selinux/myfix/* && ")
            append("(chcon -R u:object_r:system_file:s0 /system/etc/selinux/myfix 2>/dev/null || true) && ")
            append("sync && ")
            append("ls -lZ /system/lib64/libfodfix.so")
        }

        val deployResult = RootShellExecutor.exec(deployCmd, asRoot = true, timeoutMs = 12_000L)
        stageDir.deleteRecursively()

        if (!deployResult.isSuccess) {
            val detail = deployResult.stderr.ifBlank { deployResult.stdout }
            addLog(LogTag.ERR, "Échec du déploiement: $detail", LogSeverity.ERROR)
            return@withContext DeployOutcome(
                success = false,
                logs = logs,
                summary = "Échec du déploiement vers /system/lib64.",
            )
        }

        addLog(LogTag.SYS, "Déployé: ${deployResult.stdout}", LogSeverity.SUCCESS)
        addLog(LogTag.SYS, "Fichiers init & SELinux installés (mode anti-bootloop)", LogSeverity.SUCCESS)

        DeployOutcome(
            success = true,
            logs = logs,
            summary = "libfodfix.so déployé dans /system/lib64/.",
        )
    }
}
