package com.atlaspeak.data.backup

import android.content.Context
import com.atlaspeak.R
import com.atlaspeak.domain.usecase.backup.PassphraseBlocklist
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lista de contraseñas de backup comunes (BUG P0), cargada de forma perezosa
 * desde `R.raw.common_passwords` (una contraseña en minúsculas por línea, ya
 * filtradas a >= 8 caracteres) y cacheada en memoria tras la primera carga.
 */
@Singleton
class CommonPasswordBlocklist @Inject constructor(
    @ApplicationContext private val context: Context,
) : PassphraseBlocklist {
    private val loadMutex = Mutex()

    @Volatile
    private var cache: Set<String>? = null

    override suspend fun isCommon(passphraseLowercase: String): Boolean =
        load().contains(passphraseLowercase)

    private suspend fun load(): Set<String> {
        cache?.let { return it }
        return loadMutex.withLock {
            cache ?: withContext(Dispatchers.IO) {
                context.resources.openRawResource(R.raw.common_passwords)
                    .bufferedReader(Charsets.UTF_8)
                    .useLines { lines ->
                        lines.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
                    }
            }.also { cache = it }
        }
    }
}
