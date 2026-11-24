package com.example

import com.example.model.LogTag
import com.example.system.ElfInspector
import com.example.system.HalInspector
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `parseNativeLines parses CORE, HAL, and FOD diagnostic output`() {
        val sampleOutput = """
            [CORE] Initialization started
            [CORE] Library loaded
            [CORE] Android environment detected
            [CORE] Runtime test completed
            [HAL] Discovery pending
            [FOD] Live monitor active — interact with fingerprint sensor
        """.trimIndent()

        val parsed = HalInspector.parseNativeLines(sampleOutput)
        assertEquals(6, parsed.size)
        assertEquals(LogTag.CORE, parsed[0].tag)
        assertEquals("Initialization started", parsed[0].message)
        assertEquals(LogTag.HAL, parsed[4].tag)
        assertEquals("Discovery pending", parsed[4].message)
        assertEquals(LogTag.FOD, parsed[5].tag)
        assertTrue(parsed[5].message.contains("Live monitor active"))
    }

    @Test
    fun `elfInspector validates ELF64 AArch64 header and rejects invalid files`() {
        val validElf = File.createTempFile("test_arm64", ".so")
        val header = ByteArray(64)
        header[0] = 0x7F.toByte()
        header[1] = 'E'.code.toByte()
        header[2] = 'L'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = 2 // ELFCLASS64
        header[18] = 183.toByte() // EM_AARCH64 (0x00B7)
        header[19] = 0
        validElf.writeBytes(header)

        val validResult = ElfInspector.inspect(validElf)
        assertTrue(validResult.isValidArm64Elf)
        assertTrue(validResult.sha256.isNotEmpty())

        val invalidElf = File.createTempFile("invalid", ".so")
        invalidElf.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val invalidResult = ElfInspector.inspect(invalidElf)
        assertFalse(invalidResult.isValidArm64Elf)

        validElf.delete()
        invalidElf.delete()
    }
}
