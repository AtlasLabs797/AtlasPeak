package com.atlaspeak.data.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor() {
    companion object {
        const val PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PASSWORD_ITERATIONS = 600_000
        const val PASSWORD_KEY_BITS = 256
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_GCM_IV_BYTES = 12
        const val AES_GCM_TAG_BITS = 128
    }
}
