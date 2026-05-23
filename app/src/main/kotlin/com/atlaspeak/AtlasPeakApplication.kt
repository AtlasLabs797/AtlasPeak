package com.atlaspeak

import android.app.Application
import com.atlaspeak.data.db.seed.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app. Hilt genera el contenedor a partir de aqui.
 *
 * WorkManager, canales de notificacion y jobs en segundo plano se activan en sus fases.
 */
@HiltAndroidApp
class AtlasPeakApplication : Application() {
    @Inject lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            databaseSeeder.seed()
        }
    }
}
