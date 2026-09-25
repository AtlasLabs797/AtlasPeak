package com.atlaspeak.domain.usecase.security

enum class DatabaseKeyCheckResult {
    Ok,
    KeyUnavailable,
}

/**
 * Comprueba, desde `LaunchViewModel`, si la base de datos cifrada se puede abrir en este
 * dispositivo antes de decidir a que pantalla navegar. Interfaz (en vez de clase concreta) para
 * poder sustituirla por un fake puro en tests de JVM: la implementacion real toca Room/Keystore
 * y no se puede probar fuera de un dispositivo/instrumentado. Vive en `domain` (no `data`) para
 * que `LaunchViewModel` (presentation) no dependa de la capa de datos directamente
 * (StaticArchitecturePolicyTest).
 */
interface DatabaseKeyChecker {
    suspend fun check(): DatabaseKeyCheckResult
}
