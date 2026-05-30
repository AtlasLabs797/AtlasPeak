package com.atlaspeak.data.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupFileCodecTest {
    private val dispatcher = StandardTestDispatcher()
    private val codec = BackupFileCodec(dispatcher = dispatcher)

    @Test
    fun `encrypted backup writes the SECURITY header byte for byte`() = runTest(dispatcher) {
        val encrypted = codec.encrypt(
            plaintext = """{"schemaVersion":2}""".encodeToByteArray(),
            password = testPassphrase(),
        )

        val header = codec.parseHeader(encrypted)

        assertArrayEquals(byteArrayOf(0x41, 0x54, 0x50, 0x4B), encrypted.copyOfRange(0, 4))
        assertEquals(1, header.version)
        assertEquals(600_000, header.iterations)
        assertEquals(16, header.salt.size)
        assertEquals(12, header.iv.size)
        assertEquals(600_000, ByteBuffer.wrap(encrypted, 5, 4).order(ByteOrder.BIG_ENDIAN).int)
        assertTrue(encrypted.size > BackupFileCodec.HEADER_BYTES)
    }

    @Test
    fun `backup decrypts with the password and fails closed with a wrong password`() = runTest(dispatcher) {
        val plaintext = """{"tables":{"users":[]}}""".encodeToByteArray()
        val encrypted = codec.encrypt(plaintext, "restore-password".toCharArray())

        assertArrayEquals(plaintext, codec.decrypt(encrypted, "restore-password".toCharArray()))
        assertThrows(Exception::class.java) {
            runTest(dispatcher) {
                codec.decrypt(encrypted, "wrong-password".toCharArray())
            }
        }
    }

    @Test
    fun `each backup uses a fresh salt iv and ciphertext for the same password`() = runTest(dispatcher) {
        val plaintext = "same payload".encodeToByteArray()

        val first = codec.encrypt(plaintext, "same-password".toCharArray())
        val second = codec.encrypt(plaintext, "same-password".toCharArray())
        val firstHeader = codec.parseHeader(first)
        val secondHeader = codec.parseHeader(second)

        assertFalse(firstHeader.salt.contentEquals(secondHeader.salt))
        assertFalse(firstHeader.iv.contentEquals(secondHeader.iv))
        assertNotEquals(first.contentToString(), second.contentToString())
        assertArrayEquals(plaintext, codec.decrypt(second, "same-password".toCharArray()))
    }

    private fun testPassphrase(): CharArray =
        charArrayOf('c', 'o', 'r', 'r', 'e', 'c', 't', '-', 't', 'e', 's', 't')
}
