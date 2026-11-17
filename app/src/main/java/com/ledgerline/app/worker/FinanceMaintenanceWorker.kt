package com.ledgerline.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerline.app.data.repository.RecurringSuggestionStore
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.preferences.UiPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class FinanceMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dashboard: ObserveDashboard,
    private val preferences: UiPreferences,
    private val suggestions: RecurringSuggestionStore,
    private val reminders: ReminderNotifier,
    private val clock: AppClock,
    private val autoPay: com.ledgerline.app.finance.schedule.AutoPay,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            autoPay.settleDue()
            val snapshot = dashboard.current()
            preferences.recordMaintenance(
                safeToSpendMinor = snapshot.safeToSpend.amountMinor,
                currency = snapshot.currency,
                atEpochMillis = clock.now().toEpochMilli(),
            )
            suggestions.storeNew()
            reminders.deliver(snapshot, preferences)
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Local maintenance failed: ${error.javaClass.simpleName}")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "Ledgerline"
    }
}

@Singleton
class MaintenanceScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<FinanceMaintenanceWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val UNIQUE_NAME = "finance-maintenance"
    }
}
