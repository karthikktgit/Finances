package com.ledgerline.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ledgerline.app.worker.MaintenanceScheduler
import com.ledgerline.app.worker.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltAndroidApp
class LedgerlineApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var maintenanceScheduler: MaintenanceScheduler

    @Inject lateinit var reminderNotifier: ReminderNotifier

    @Inject lateinit var todayReminder: com.ledgerline.app.worker.TodayReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannels()
        maintenanceScheduler.ensureScheduled()
        // The daily "today's payments" reminder, at the time chosen in Reminders.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { runCatching { todayReminder.schedule() } }
    }
}
