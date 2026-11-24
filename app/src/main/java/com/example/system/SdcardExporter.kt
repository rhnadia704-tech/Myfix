package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.example.model.DiagnosticLogLine
import com.example.model.LogSeverity
import com.example.model.LogTag
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SdcardExporter(private val context: Context) {

    data class ExportOutcome(
        val success: Boolean,
        val requiresPermission: Boolean = false,
        val logs: List<DiagnosticLogLine>,
        val summary: String,
    )

    fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun exportToSdcard(): ExportOutcome = withContext(Dispatchers.IO) {
        val logs = mutableListOf<DiagnosticLogLine>()
        fun addLog(tag: LogTag, msg: String, sev: LogSeverity = LogSeverity.INFO) {
            logs += HalInspector.nextLine(tag, msg, sev)
        }

        val hasPerm = hasStorageAccess()
        val hasRoot = if (!hasPerm) RootShellExecutor.isRootAvailable() else false
        if (!hasPerm && !hasRoot) {
            return@withContext ExportOutcome(
                success = false,
                requiresPermission = true,
                logs = emptyList(),
                summary = "Permission de stockage requise pour /sdcard/MYFix.",
            )
        }

        val targetRoot = File(Environment.getExternalStorageDirectory(), "MYFix")
        val stageRoot = File(context.cacheDir, "myfix_export").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            File(stageRoot, "lib64").mkdirs()
            File(stageRoot, "selinux").mkdirs()
            File(stageRoot, "init").mkdirs()

            val assetMappings = listOf(
                "myfix/selinux/fodfix.te" to "selinux/fodfix.te",
                "myfix/selinux/fodfix_file_contexts" to "selinux/fodfix_file_contexts",
                "myfix/selinux/plat_sepolicy_fodfix.cil" to "selinux/plat_sepolicy_fodfix.cil",
                "myfix/init/fodfix.rc" to "init/fodfix.rc",
                "myfix/install_gsi.sh" to "install_gsi.sh",
                "myfix/uninstall_gsi.sh" to "uninstall_gsi.sh",
            )
            for ((assetPath, relOut) in assetMappings) {
                context.assets.open(assetPath).use { input ->
                    File(stageRoot, relOut).outputStream().use { output -> input.copyTo(output) }
                }
            }

            val activeLib = BinaryManager.resolveActiveLibraryFile(context)
            if (activeLib != null) {
                val destLib = File(stageRoot, "lib64/libfodfix.so")
                activeLib.copyTo(destLib, overwrite = true)
                addLog(
                    LogTag.CORE,
                    "Binaire arm64-v8a exporté: lib64/libfodfix.so (${destLib.length()} octets)",
                    LogSeverity.SUCCESS,
                )
            } else {
                val readme = File(stageRoot, "lib64/BUILD_NATIVE_README.txt")
                readme.writeText(
                    "Aucun libfodfix.so n'est présent dans cet APK ou importé.\n" +
                        "Importez libfodfix.so via Importer ou compilez via GitHub Actions.\n",
                )
                addLog(
                    LogTag.SYS,
                    "libfodfix.so absent (fichiers SELinux/init/scripts exportés).",
                    LogSeverity.WARNING,
                )
            }

            if (hasPerm) {
                targetRoot.mkdirs()
                stageRoot.copyRecursively(targetRoot, overwrite = true)
            } else {
                val copyCmd = "mkdir -p '${targetRoot.absolutePath}' && cp -rf '${stageRoot.absolutePath}'/* '${targetRoot.absolutePath}/'"
                val res = RootShellExecutor.exec(copyCmd, asRoot = true, timeoutMs = 8_000L)
                if (!res.isSuccess) {
                    addLog(LogTag.ERR, "Échec écriture root vers ${targetRoot.absolutePath}: ${res.stderr}", LogSeverity.ERROR)
                    return@withContext ExportOutcome(false, false, logs, "Impossible d'écrire dans /sdcard/MYFix.")
                }
            }

            addLog(LogTag.SYS, "Exporté dans ${targetRoot.absolutePath}", LogSeverity.SUCCESS)
            addLog(LogTag.SYS, "Contenu: lib64/, selinux/, init/, install_gsi.sh, uninstall_gsi.sh", LogSeverity.INFO)

            ExportOutcome(
                success = true,
                requiresPermission = false,
                logs = logs,
                summary = "Exporté vers ${targetRoot.absolutePath}",
            )
        } catch (t: Throwable) {
            addLog(LogTag.ERR, "Erreur export: ${t.message}", LogSeverity.ERROR)
            ExportOutcome(false, false, logs, "Erreur d'export vers /sdcard/MYFix.")
        } finally {
            stageRoot.deleteRecursively()
        }
    }
}
