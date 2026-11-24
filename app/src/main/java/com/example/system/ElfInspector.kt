package com.example.system

import java.io.File
import java.security.MessageDigest

data class ElfValidationResult(
    val isValidArm64Elf: Boolean,
    val summary: String,
    val sha256: String,
)

object ElfInspector {

    private const val EM_AARCH64 = 183

    fun inspect(file: File): ElfValidationResult {
        if (!file.exists() || !file.isFile) {
            return ElfValidationResult(false, "Fichier introuvable: ${file.absolutePath}", "")
        }

        val bytes = file.readBytes()
        if (bytes.size < 24) {
            return ElfValidationResult(false, "Fichier ELF tronqué (${bytes.size} octets)", "")
        }

        val magicOk =
            bytes[0] == 0x7F.toByte() &&
                bytes[1] == 'E'.code.toByte() &&
                bytes[2] == 'L'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
        if (!magicOk) {
            return ElfValidationResult(false, "En-tête ELF invalide", "")
        }

        val elfClass = bytes[4].toInt() and 0xFF
        val eMachine = (bytes[18].toInt() and 0xFF) or ((bytes[19].toInt() and 0xFF) shl 8)
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val isArm64 = elfClass == 2 && eMachine == EM_AARCH64
        val summary = if (isArm64) {
            "ELF64 AArch64 valide (${bytes.size} octets)"
        } else {
            "Architecture ELF incompatible (class=$elfClass, machine=$eMachine)"
        }
        return ElfValidationResult(isArm64, summary, sha256)
    }
}
