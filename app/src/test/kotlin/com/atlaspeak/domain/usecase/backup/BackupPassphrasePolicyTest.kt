package com.atlaspeak.domain.usecase.backup

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupPassphrasePolicyTest {
    private val policy = BackupPassphrasePolicy(PassphraseBlocklist { it == "password123" })

    @Test
    fun `passphrase shorter than 8 characters is too short`() = runTest {
        val result = policy.evaluate("short12".toCharArray(), "short12".toCharArray())

        assertEquals(BackupPassphraseValidation.TooShort, result)
    }

    @Test
    fun `empty passphrase is too short`() = runTest {
        val result = policy.evaluate(CharArray(0), CharArray(0))

        assertEquals(BackupPassphraseValidation.TooShort, result)
    }

    @Test
    fun `passphrase in the common blocklist is rejected regardless of case`() = runTest {
        val result = policy.evaluate("PASSWORD123".toCharArray(), "PASSWORD123".toCharArray())

        assertEquals(BackupPassphraseValidation.Common, result)
    }

    @Test
    fun `mismatched confirmation is rejected`() = runTest {
        val result = policy.evaluate("correct horse battery".toCharArray(), "correct horse batteryy".toCharArray())

        assertEquals(BackupPassphraseValidation.Mismatch, result)
    }

    @Test
    fun `a long, uncommon, confirmed passphrase is valid`() = runTest {
        val result = policy.evaluate("correct horse battery".toCharArray(), "correct horse battery".toCharArray())

        assertEquals(BackupPassphraseValidation.Valid, result)
    }

    @Test
    fun `no complexity rules are enforced, only length and blocklist`() = runTest {
        // AGENTS.md 2.2: sin exigir mayusculas, digitos o simbolos.
        val result = policy.evaluate("allletterslowercase".toCharArray(), "allletterslowercase".toCharArray())

        assertEquals(BackupPassphraseValidation.Valid, result)
    }
}
