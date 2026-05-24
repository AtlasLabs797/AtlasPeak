package com.atlaspeak.data.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupWorkSchedulerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(BackupWorkScheduler.WORK_NAME)
    }

    @Test
    fun scheduleDailyKeepsOneUniqueBackupWork() {
        val scheduler = BackupWorkScheduler(context)

        scheduler.scheduleDaily()
        scheduler.scheduleDaily()

        val workInfos = workManager.awaitUniqueWorkInfos(BackupWorkScheduler.WORK_NAME)

        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }

    private fun WorkManager.awaitUniqueWorkInfos(name: String): List<WorkInfo> {
        val latch = CountDownLatch(1)
        var observed: List<WorkInfo> = emptyList()
        val liveData = getWorkInfosForUniqueWorkLiveData(name)
        val observer = Observer<List<WorkInfo>> { workInfos ->
            if (workInfos.isNotEmpty()) {
                observed = workInfos
                latch.countDown()
            }
        }
        return try {
            liveData.observeForever(observer)
            assertTrue("Timed out waiting for unique work '$name'", latch.await(5, TimeUnit.SECONDS))
            observed
        } finally {
            liveData.removeObserver(observer)
        }
    }
}
