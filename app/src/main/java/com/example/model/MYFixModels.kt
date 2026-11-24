package com.example.model

enum class LogTag {
    CORE,
    HAL,
    FOD,
    SYS,
    ERR,
}

enum class LogSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class DiagnosticLogLine(
    val id: Long,
    val tag: LogTag,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO,
) {
    fun formatted(): String = "[$tag] $message"
}

data class ActiveBinaryInfo(
    val isImported: Boolean = false,
    val displayName: String = "Aucun",
    val fileSizeBytes: Long = 0L,
    val sha256Short: String = "",
    val isAvailable: Boolean = false,
)

data class SystemStatus(
    val primaryAbi: String = "unknown",
    val androidVersion: String = "?",
    val isArm64: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isDeployedInSystemLib64: Boolean = false,
    val seLinuxMode: String = "Unknown",
    val isNativeLibBundled: Boolean = false,
    val activeBinary: ActiveBinaryInfo = ActiveBinaryInfo(),
    val isChecking: Boolean = true,
)

enum class ActiveOperation {
    NONE,
    DIAGNOSTIC_DEPLOYED,
    DIAGNOSTIC_SANDBOX,
    DEPLOY,
    EXPORT,
    IMPORT,
}

data class MYFixUiState(
    val status: SystemStatus = SystemStatus(),
    val activeOperation: ActiveOperation = ActiveOperation.NONE,
    val isLiveMonitoring: Boolean = false,
    val logs: List<DiagnosticLogLine> = emptyList(),
    val bannerMessage: String? = null,
    val bannerIsError: Boolean = false,
    val showStoragePermissionDialog: Boolean = false,
)
