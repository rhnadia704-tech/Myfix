package com.example.system

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.ActiveBinaryInfo
import com.example.nativebridge.FodFixNative
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BinaryManager {

    private const val IMPORTED_DIR = "imported"
    private const val IMPORTED_FILE = "libfodfix.so"
    private const val IMPORTED_META_FILE = "imported_name.txt"

    data class ImportOutcome(
        val success: Boolean,
        val message: String,
        val binaryInfo: ActiveBinaryInfo? = null,
    )

    fun getImportedLibraryFile(context: Context): File? {
        val file = File(File(context.filesDir, IMPORTED_DIR), IMPORTED_FILE)
        return file.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    fun resolveActiveLibraryFile(context: Context): File? {
        return getImportedLibraryFile(context) ?: FodFixNative.findBundledLibrary(context)
    }

    fun getActiveBinaryInfo(context: Context): ActiveBinaryInfo {
        val imported = getImportedLibraryFile(context)
        if (imported != null) {
            val elf = ElfInspector.inspect(imported)
            val metaFile = File(File(context.filesDir, IMPORTED_DIR), IMPORTED_META_FILE)
            val displayName = if (metaFile.exists()) metaFile.readText().trim().ifBlank { IMPORTED_FILE } else IMPORTED_FILE
            return ActiveBinaryInfo(
                isImported = true,
                displayName = displayName,
                fileSizeBytes = imported.length(),
                sha256Short = elf.sha256.take(12),
                isAvailable = elf.isValidArm64Elf,
            )
        }

        val bundled = FodFixNative.findBundledLibrary(context)
        if (bundled != null) {
            val elf = ElfInspector.inspect(bundled)
            return ActiveBinaryInfo(
                isImported = false,
                displayName = "libfodfix.so (intégré)",
                fileSizeBytes = bundled.length(),
                sha256Short = elf.sha256.take(12),
                isAvailable = true,
            )
        }

        return ActiveBinaryInfo(
            isImported = false,
            displayName = "Aucun binaire présent",
            isAvailable = false,
        )
    }

    suspend fun importBinaryFromUri(context: Context, uri: Uri): ImportOutcome =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, IMPORTED_DIR).apply { mkdirs() }
            val tempFile = File(dir, "pending_import.so")

            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportOutcome(false, "Impossible d'ouvrir le fichier sélectionné.")
                input.use { stream ->
                    tempFile.outputStream().use { out -> stream.copyTo(out) }
                }

                val elf = ElfInspector.inspect(tempFile)
                if (!elf.isValidArm64Elf) {
                    tempFile.delete()
                    return@withContext ImportOutcome(
                        success = false,
                        message = "Fichier rejeté : ${elf.summary}",
                    )
                }

                val targetFile = File(dir, IMPORTED_FILE)
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val pickedName = resolveDisplayName(context, uri) ?: IMPORTED_FILE
                File(dir, IMPORTED_META_FILE).writeText(pickedName)

                val info = getActiveBinaryInfo(context)
                ImportOutcome(
                    success = true,
                    message = "Importé : $pickedName (${elf.sha256.take(12)}…)",
                    binaryInfo = info,
                )
            } catch (t: Throwable) {
                tempFile.delete()
                ImportOutcome(false, "Erreur d'importation : ${t.message ?: t::class.java.simpleName}")
            }
        }

    fun clearImportedBinary(context: Context) {
        val dir = File(context.filesDir, IMPORTED_DIR)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }
}
