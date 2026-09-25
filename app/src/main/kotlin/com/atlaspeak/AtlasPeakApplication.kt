package com.atlaspeak

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.atlaspeak.data.backup.BackupWorkScheduler
import com.atlaspeak.data.backup.LocalBackupExportManager
import com.atlaspeak.data.notification.AtlasPeakNotificationHelper
import com.atlaspeak.data.db.seed.DatabaseSeeder
import com.atlaspeak.data.security.DatabaseKeyUnavailableException
import com.atlaspeak.domain.repository.NotificationScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada de la app. Hilt genera el contenedor a partir de aqui.
 *
 * WorkManager usa HiltWorkerFactory porque los workers dependen de repositorios Room.
 *
 * P1 (auditoria): `databaseSeeder`, `notificationScheduler` y `localBackupExportManager` se
 * inyectan como `dagger.Lazy` para que construir esta clase (inyeccion de campos, que ocurre en
 * el hilo principal) no abra la base de datos cifrada. Solo se materializan (`.get()`) dentro
 * de la corrutina IO de abajo.
 */
@HiltAndroidApp
class AtlasPeakApplication : Application(), Configuration.Provider {
    @Inject lateinit var databaseSeeder: Lazy<DatabaseSeeder>
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: AtlasPeakNotificationHelper
    @Inject lateinit var notificationScheduler: Lazy<NotificationScheduler>
    @Inject lateinit var backupWorkScheduler: BackupWorkScheduler
    @Inject lateinit var localBackupExportManager: Lazy<LocalBackupExportManager>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        System.loadLibrary("sqlcipher")
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                databaseSeeder.get().seed()
                notificationScheduler.get().rescheduleAll()
                localBackupExportManager.get().cleanupExpiredExports()
            } catch (error: DatabaseKeyUnavailableException) {
                // La base de datos cifrada no se puede abrir en este dispositivo (Keystore
                // corrupto o keyset perdido, problema conocido en ciertos OEM). No hay nada
                // que sembrar/planificar/limpiar; LaunchViewModel (DatabaseKeyChecker) detecta
                // lo mismo y enruta a Recovery. Lo importante es no crashear el proceso aqui.
            }
            backupWorkScheduler.scheduleDaily()
        }
    }
}
