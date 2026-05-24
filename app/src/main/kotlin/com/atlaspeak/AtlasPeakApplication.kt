package com.atlaspeak

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.atlaspeak.data.backup.BackupWorkScheduler
import com.atlaspeak.data.notification.AtlasPeakNotificationHelper
import com.atlaspeak.data.db.seed.DatabaseSeeder
import com.atlaspeak.domain.repository.NotificationScheduler
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
 */
@HiltAndroidApp
class AtlasPeakApplication : Application(), Configuration.Provider {
    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: AtlasPeakNotificationHelper
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var backupWorkScheduler: BackupWorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            databaseSeeder.seed()
            notificationScheduler.rescheduleAll()
            backupWorkScheduler.scheduleDaily()
        }
    }
}
