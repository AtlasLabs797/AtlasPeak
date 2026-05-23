package com.atlaspeak

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Punto de entrada de la app. Hilt genera el contenedor a partir de aquí.
 *
 * Fase 0/1: aquí se inicializará WorkManager con la factory de Hilt, se crearán los
 * canales de notificación y se disparará la apertura de la DB cifrada (SQLCipher).
 * Ver SPEC.md §2.8 (canales) y DOCS_TECNICA.md §4 (DB/clave).
 */
@HiltAndroidApp
class AtlasPeakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO(Fase 1): inicializar canales de notificación.
        // TODO(Fase 1): inicializar EncryptionManager / apertura de DB.
    }
}
