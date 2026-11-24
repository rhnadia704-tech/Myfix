package com.example.system

import android.content.Context
import android.os.Build
import com.example.model.DiagnosticLogLine
import com.example.model.LogSeverity
import com.example.model.LogTag
import com.example.model.SystemStatus
import com.example.nativebridge.FodFixNative
import java.io.File
import java.util.concurrent.atomic.AtomicLong

object HalInspector {

    const val SYSTEM_LIB64_PATH = "/system/lib64/libfodfix.so"
    private val lineIdCounter = AtomicLong(1L)

    fun nextLine(
        tag: LogTag,
        message: String,
        severity: LogSeverity = LogSeverity.INFO,
    ): DiagnosticLogLine = DiagnosticLogLine(
        id = lineIdCounter.getAndIncrement(),
        tag = tag,
        message = message,
        severity = severity,
    )

    fun parseSingleNativeLine(rawLine: String): DiagnosticLogLine? {
        val line = rawLine.trim()
        if (line.isEmpty()) return null
        return when {
            line.startsWith("[CORE]") -> {
                val msg = line.removePrefix("[CORE]").trim()
                val sev = if (msg.contains("completed", ignoreCase = true) ||
                    msg.contains("loaded", ignoreCase = true) ||
                    msg.contains("detected", ignoreCase = true) ||
                    msg.contains("Verified", ignoreCase = true)
                ) {
                    LogSeverity.SUCCESS
                } else {
                    LogSeverity.INFO
                }
                nextLine(LogTag.CORE, msg, sev)
            }
            line.startsWith("[HAL]") -> {
                val msg = line.removePrefix("[HAL]").trim()
                val sev = when {
                    msg.contains("exited", ignoreCase = true) ||
                        msg.contains("unavailable", ignoreCase = true) -> LogSeverity.WARNING
                    msg.contains("Module", ignoreCase = true) ||
                        msg.contains("Process", ignoreCase = true) ||
                        msg.contains("Active", ignoreCase = true) ||
                        msg.contains("attached", ignoreCase = true) -> LogSeverity.SUCCESS
                    else -> LogSeverity.INFO
                }
                nextLine(LogTag.HAL, msg, sev)
            }
            line.startsWith("[FOD]") -> {
                val msg = line.removePrefix("[FOD]").trim()
                val sev = when {
                    msg.contains("changed", ignoreCase = true) ||
                        msg.contains("active", ignoreCase = true) -> LogSeverity.SUCCESS
                    msg.contains("No direct", ignoreCase = true) ||
                        msg.contains("stopped", ignoreCase = true) -> LogSeverity.WARNING
                    else -> LogSeverity.INFO
                }
                nextLine(LogTag.FOD, msg, sev)
            }
            line.startsWith("[ERR]") -> {
                nextLine(LogTag.ERR, line.removePrefix("[ERR]").trim(), LogSeverity.ERROR)
            }
            line.startsWith("[SYS]") -> {
                nextLine(LogTag.SYS, line.removePrefix("[SYS]").trim(), LogSeverity.INFO)
            }
            else -> nextLine(LogTag.CORE, line, LogSeverity.INFO)
        }
    }

    fun parseNativeLines(rawOutput: String): List<DiagnosticLogLine> {
        return rawOutput.lineSequence()
            .mapNotNull { parseSingleNativeLine(it) }
            .toList()
    }

    suspend fun fetchSystemStatus(context: Context): SystemStatus {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        val primaryAbi = abis.firstOrNull() ?: "unknown"
        val isArm64 = abis.any { it == "arm64-v8a" }

        val rootAvailable = RootShellExecutor.isRootAvailable()
        val seLinux = readSeLinuxMode(rootAvailable)
        val deployedInSystem = checkSystemLibExists(rootAvailable)
        val bundledPresent = FodFixNative.findBundledLibrary(context) != null
        val activeBinary = BinaryManager.getActiveBinaryInfo(context)

        return SystemStatus(
            primaryAbi = primaryAbi,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            isArm64 = isArm64,
            isRootAvailable = rootAvailable,
            isDeployedInSystemLib64 = deployedInSystem,
            seLinuxMode = seLinux,
            isNativeLibBundled = bundledPresent,
            activeBinary = activeBinary,
            isChecking = false,
        )
    }

    private suspend fun readSeLinuxMode(rootAvailable: Boolean): String {
        val shRes = RootShellExecutor.exec("getenforce", asRoot = false, timeoutMs = 2_000L)
        if (shRes.isSuccess && shRes.stdout.isNotBlank()) {
            return shRes.stdout.trim()
        }
        if (rootAvailable) {
            val suRes = RootShellExecutor.exec("getenforce", asRoot = true, timeoutMs = 2_500L)
            if (suRes.isSuccess && suRes.stdout.isNotBlank()) {
                return suRes.stdout.trim()
            }
        }
        return "Restricted"
    }

    private suspend fun checkSystemLibExists(rootAvailable: Boolean): Boolean {
        val localFile = File(SYSTEM_LIB64_PATH)
        if (localFile.exists()) return true
        if (!rootAvailable) return false
        val res = RootShellExecutor.exec(
            "[ -f $SYSTEM_LIB64_PATH ] && echo PRESENT",
            asRoot = true,
            timeoutMs = 2_500L,
        )
        return res.isSuccess && res.stdout.contains("PRESENT")
    }
}
