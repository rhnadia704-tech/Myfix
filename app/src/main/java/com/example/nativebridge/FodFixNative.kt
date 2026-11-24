package com.example.nativebridge

import android.content.Context
import com.example.system.BinaryManager
import java.io.File

interface NativeLogListener {
    fun onNativeLogLine(line: String)
}

object FodFixNative {

    private var loadedSourcePath: String? = null
    var isLoaded: Boolean = false
        private set
    var loadError: String? = null
        private set

    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (isLoaded) return true

        val imported = BinaryManager.getImportedLibraryFile(context)
        if (imported != null) {
            return try {
                System.load(imported.absolutePath)
                isLoaded = true
                loadedSourcePath = imported.absolutePath
                loadError = null
                true
            } catch (t: Throwable) {
                loadError = t.message ?: t::class.java.simpleName
                tryLoadBundled()
            }
        }
        return tryLoadBundled()
    }

    private fun tryLoadBundled(): Boolean {
        return try {
            System.loadLibrary("fodfix")
            isLoaded = true
            loadedSourcePath = "bundled"
            loadError = null
            true
        } catch (t: Throwable) {
            isLoaded = false
            loadError = t.message ?: t::class.java.simpleName
            false
        }
    }

    fun findBundledLibrary(context: Context): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        val libFile = File(nativeDir, "libfodfix.so")
        return libFile.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    fun runDiagnosticForActiveBinary(context: Context): Result<String> {
        if (!ensureLoaded(context)) {
            return Result.failure(
                IllegalStateException(loadError ?: "libfodfix.so non chargé"),
            )
        }

        val imported = BinaryManager.getImportedLibraryFile(context)
        if (imported != null && loadedSourcePath != imported.absolutePath) {
            return runCatching { nativeVerifySystemLibrary(imported.absolutePath) }
        }

        return runCatching {
            val core = nativeRunDiagnostic()
            val hal = nativeProbeVendorHals()
            buildString {
                append(core)
                if (!core.endsWith("\n")) append('\n')
                append(hal)
            }
        }
    }

    fun verifySystemLibrary(context: Context, path: String): Result<String> {
        if (!ensureLoaded(context)) {
            return Result.failure(
                IllegalStateException(loadError ?: "libfodfix.so non chargé"),
            )
        }
        return runCatching { nativeVerifySystemLibrary(path) }
    }

    fun startLiveMonitor(
        context: Context,
        targetPath: String?,
        listener: NativeLogListener,
    ): Result<Int> {
        if (!ensureLoaded(context)) {
            return Result.failure(IllegalStateException(loadError ?: "libfodfix.so non chargé"))
        }

        val imported = BinaryManager.getImportedLibraryFile(context)
        val externalPath = when {
            !targetPath.isNullOrBlank() -> targetPath
            imported != null && loadedSourcePath != imported.absolutePath -> imported.absolutePath
            else -> null
        }

        return runCatching {
            if (externalPath != null) {
                nativeStartExternalLiveMonitor(externalPath, listener)
            } else {
                nativeStartLiveMonitor(listener)
            }
        }
    }

    fun stopLiveMonitor() {
        if (!isLoaded) return
        runCatching { nativeStopLiveMonitor() }
    }

    private external fun nativeGetVersion(): String
    private external fun nativeRunDiagnostic(): String
    private external fun nativeProbeVendorHals(): String
    private external fun nativeVerifySystemLibrary(path: String): String
    private external fun nativeStartLiveMonitor(listener: NativeLogListener): Int
    private external fun nativeStopLiveMonitor()
    private external fun nativeStartExternalLiveMonitor(path: String, listener: NativeLogListener): Int
}
