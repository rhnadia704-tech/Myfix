package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ActiveOperation
import com.example.model.DiagnosticLogLine
import com.example.model.LogSeverity
import com.example.model.LogTag
import com.example.model.MYFixUiState
import com.example.nativebridge.FodFixNative
import com.example.nativebridge.NativeLogListener
import com.example.system.BinaryManager
import com.example.system.GsiDeployManager
import com.example.system.HalInspector
import com.example.system.SdcardExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MYFixViewModel(application: Application) : AndroidViewModel(application) {

    private val deployManager = GsiDeployManager(application)
    private val sdcardExporter = SdcardExporter(application)

    private val _uiState = MutableStateFlow(MYFixUiState())
    val uiState: StateFlow<MYFixUiState> = _uiState.asStateFlow()

    private val liveListener = object : NativeLogListener {
        override fun onNativeLogLine(line: String) {
            val parsed = HalInspector.parseSingleNativeLine(line) ?: return
            _uiState.update { state ->
                val updated = (state.logs + parsed).takeLast(400)
                val stopped = parsed.tag == LogTag.FOD &&
                    parsed.message.contains("Live monitor stopped", ignoreCase = true)
                state.copy(
                    logs = updated,
                    isLiveMonitoring = if (stopped) false else state.isLiveMonitoring,
                )
            }
        }
    }

    init {
        refreshSystemStatus()
    }

    override fun onCleared() {
        FodFixNative.stopLiveMonitor()
        super.onCleared()
    }

    fun refreshSystemStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = it.status.copy(isChecking = true)) }
            val status = HalInspector.fetchSystemStatus(getApplication())
            _uiState.update { it.copy(status = status) }
        }
    }

    fun runDiagnosticWithoutDeploy() {
        if (_uiState.value.activeOperation != ActiveOperation.NONE) return
        viewModelScope.launch {
            FodFixNative.stopLiveMonitor()
            _uiState.update {
                it.copy(
                    activeOperation = ActiveOperation.DIAGNOSTIC_SANDBOX,
                    isLiveMonitoring = false,
                    logs = emptyList(),
                    bannerMessage = null,
                )
            }

            val app = getApplication<Application>()
            val nativeRes = FodFixNative.runDiagnosticForActiveBinary(app)
            val newLogs = if (nativeRes.isSuccess) {
                HalInspector.parseNativeLines(nativeRes.getOrThrow())
            } else {
                val err = nativeRes.exceptionOrNull()?.message ?: "libfodfix.so indisponible"
                listOf(
                    HalInspector.nextLine(
                        LogTag.ERR,
                        "libfodfix.so non chargé ($err) — utilisez Importer ou compilez avec NDK",
                        LogSeverity.ERROR,
                    ),
                )
            }

            val liveStarted = if (nativeRes.isSuccess) {
                FodFixNative.startLiveMonitor(app, targetPath = null, listener = liveListener)
                    .getOrDefault(-1) == 0
            } else {
                false
            }

            val freshStatus = HalInspector.fetchSystemStatus(app)
            _uiState.update {
                it.copy(
                    status = freshStatus,
                    activeOperation = ActiveOperation.NONE,
                    isLiveMonitoring = liveStarted,
                    logs = newLogs,
                    bannerMessage = if (nativeRes.isFailure) {
                        "libfodfix.so non chargé"
                    } else {
                        null
                    },
                    bannerIsError = nativeRes.isFailure,
                )
            }
        }
    }

    fun runDiagnosticDeployed() {
        if (_uiState.value.activeOperation != ActiveOperation.NONE) return
        viewModelScope.launch {
            FodFixNative.stopLiveMonitor()
            _uiState.update {
                it.copy(
                    activeOperation = ActiveOperation.DIAGNOSTIC_DEPLOYED,
                    isLiveMonitoring = false,
                    logs = emptyList(),
                    bannerMessage = null,
                )
            }

            val app = getApplication<Application>()
            val verifyRes = FodFixNative.verifySystemLibrary(app, HalInspector.SYSTEM_LIB64_PATH)
            val newLogs: List<DiagnosticLogLine>
            val verifySuccess: Boolean

            if (verifyRes.isSuccess) {
                val raw = verifyRes.getOrThrow()
                newLogs = HalInspector.parseNativeLines(raw)
                verifySuccess = newLogs.none { it.tag == LogTag.ERR }
            } else {
                val err = verifyRes.exceptionOrNull()?.message ?: "libfodfix.so non chargé"
                newLogs = listOf(
                    HalInspector.nextLine(
                        LogTag.ERR,
                        "Impossible d'exécuter libfodfix ($err)",
                        LogSeverity.ERROR,
                    ),
                )
                verifySuccess = false
            }

            val liveStarted = if (verifySuccess) {
                FodFixNative.startLiveMonitor(
                    app,
                    targetPath = HalInspector.SYSTEM_LIB64_PATH,
                    listener = liveListener,
                ).getOrDefault(-1) == 0
            } else {
                false
            }

            val freshStatus = HalInspector.fetchSystemStatus(app)
            _uiState.update {
                it.copy(
                    status = freshStatus,
                    activeOperation = ActiveOperation.NONE,
                    isLiveMonitoring = liveStarted,
                    logs = newLogs,
                    bannerMessage = if (!verifySuccess) {
                        "Échec vérification /system/lib64/libfodfix.so"
                    } else {
                        null
                    },
                    bannerIsError = !verifySuccess,
                )
            }
        }
    }

    fun stopLiveMonitor() {
        FodFixNative.stopLiveMonitor()
        _uiState.update { it.copy(isLiveMonitoring = false) }
    }

    fun deployToSystem() {
        if (_uiState.value.activeOperation != ActiveOperation.NONE) return
        viewModelScope.launch {
            FodFixNative.stopLiveMonitor()
            _uiState.update {
                it.copy(
                    activeOperation = ActiveOperation.DEPLOY,
                    isLiveMonitoring = false,
                    bannerMessage = null,
                )
            }

            val outcome = deployManager.deployToSystem()
            val freshStatus = HalInspector.fetchSystemStatus(getApplication())

            _uiState.update {
                it.copy(
                    status = freshStatus,
                    activeOperation = ActiveOperation.NONE,
                    logs = outcome.logs,
                    bannerMessage = outcome.summary,
                    bannerIsError = !outcome.success,
                )
            }
        }
    }

    fun exportToSdcard() {
        if (_uiState.value.activeOperation != ActiveOperation.NONE) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeOperation = ActiveOperation.EXPORT,
                    bannerMessage = null,
                )
            }

            val outcome = sdcardExporter.exportToSdcard()
            _uiState.update {
                it.copy(
                    activeOperation = ActiveOperation.NONE,
                    logs = if (outcome.logs.isNotEmpty()) outcome.logs else it.logs,
                    bannerMessage = if (outcome.requiresPermission) null else outcome.summary,
                    bannerIsError = !outcome.success && !outcome.requiresPermission,
                    showStoragePermissionDialog = outcome.requiresPermission,
                )
            }
        }
    }

    fun importBinaryFromUri(uri: Uri) {
        if (_uiState.value.activeOperation != ActiveOperation.NONE) return
        viewModelScope.launch {
            FodFixNative.stopLiveMonitor()
            _uiState.update {
                it.copy(
                    activeOperation = ActiveOperation.IMPORT,
                    isLiveMonitoring = false,
                    bannerMessage = null,
                )
            }

            val app = getApplication<Application>()
            val outcome = BinaryManager.importBinaryFromUri(app, uri)
            val freshStatus = HalInspector.fetchSystemStatus(app)

            _uiState.update {
                it.copy(
                    status = freshStatus,
                    activeOperation = ActiveOperation.NONE,
                    bannerMessage = outcome.message,
                    bannerIsError = !outcome.success,
                )
            }
        }
    }

    fun resetToBundledBinary() {
        viewModelScope.launch {
            FodFixNative.stopLiveMonitor()
            val app = getApplication<Application>()
            BinaryManager.clearImportedBinary(app)
            val freshStatus = HalInspector.fetchSystemStatus(app)
            _uiState.update {
                it.copy(
                    status = freshStatus,
                    isLiveMonitoring = false,
                    bannerMessage = "Binaire réinitialisé sur la version intégrée",
                    bannerIsError = false,
                )
            }
        }
    }

    fun showTransientBanner(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(bannerMessage = message, bannerIsError = isError) }
    }

    fun dismissStorageDialog() {
        _uiState.update { it.copy(showStoragePermissionDialog = false) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), bannerMessage = null) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }
}
