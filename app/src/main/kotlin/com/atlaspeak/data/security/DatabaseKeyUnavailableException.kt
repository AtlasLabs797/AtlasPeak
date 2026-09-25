package com.atlaspeak.data.security

/**
 * La passphrase de la base de datos SQLCipher no se pudo obtener ni derivar (SEC-010).
 *
 * Se lanza cuando el Keystore de Android esta corrupto o el keyset asociado desaparecio
 * (problema conocido en ciertos OEM), y la base de datos cifrada ya existe en disco: en ese
 * caso NUNCA se genera una passphrase nueva en silencio, porque dejaria la base de datos
 * existente inaccesible para siempre (perdida de datos). La UI debe ofrecer al usuario
 * borrar los datos locales y empezar de nuevo (ver `presentation/recovery`).
 */
class DatabaseKeyUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
