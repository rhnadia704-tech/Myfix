package com.example.system

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object RootShellExecutor {

    suspend fun exec(
        command: String,
        asRoot: Boolean = false,
        timeoutMs: Long = 10_000L,
    ): ShellResult = withContext(Dispatchers.IO) {
        val cmdArray = if (asRoot) {
            listOf("su", "-c", command)
        } else {
            listOf("sh", "-c", command)
        }

        try {
            val process = ProcessBuilder(cmdArray).start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                ShellResult(-1, stdout, "Command timeout after ${timeoutMs}ms")
            } else {
                ShellResult(process.exitValue(), stdout, stderr)
            }
        } catch (t: Throwable) {
            ShellResult(-1, "", t.message ?: t::class.java.simpleName)
        }
    }

    suspend fun isRootAvailable(): Boolean {
        val result = exec("id", asRoot = true, timeoutMs = 4_000L)
        return result.isSuccess && result.stdout.contains("uid=0")
    }
}
