package com.atlaspeak.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkModuleTest {
    @Test
    fun `Drive OkHttp client has bounded timeouts`() {
        val client = NetworkModule.provideOkHttpClient()

        assertEquals(20_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
        assertEquals(120_000, client.callTimeoutMillis)
    }
}
