package com.ledgerline.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ledgerline.app.domain.model.CashFlowDirection
import com.ledgerline.app.domain.repository.TransactionRepository
import com.ledgerline.app.domain.time.AppClock
import com.ledgerline.app.domain.usecase.ObserveDashboard
import com.ledgerline.app.finance.balance.BalanceMath
import com.ledgerline.app.finance.money.MoneyFormat
import com.ledgerline.app.preferences.UiPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The time of the next reminder: today at [minutes] past midnight if that is still ahead, otherwise tomorrow. */
object ReminderTime {
    fun next(now: LocalDateTime, minutes: Int): LocalDateTime {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}

/**
 * The bills a reminder lists for [date]: only ones really due that day and not paid. The forecast list moves bills
 * that are overdue onto today so the cash line counts them; a reminder must not, or an old unpaid bill is announced
 * as "1 payment today" every day while Home's summary (rightly) says nothing is due.
 */
object ReminderLines {
    fun billsOn(schedule: List<com.ledgerline.app.domain.model.ScheduledOccurrence>, date: java.time.LocalDate): List<Pair<String, Long>> =
        schedule.filter { it.date == date && !it.overdue && it.direction == CashFlowDirection.OUTFLOW }
            .distinctBy { it.recurringId to it.date }
            .map { it.name to it.amountMinor }
}

/** The statement reminder goes out once per cycle, on its first day. */
object CycleReminder {
    fun isFirstDay(today: java.time.LocalDate, cycleStart: java.time.LocalDate): Boolean = today == cycleStart
}

/**
 * Each day at your chosen time: a notification listing that day's payments (bills due, and spends you planned for
 * today). Nothing is sent on a day with none. It schedules the next one as it finishes.
 */
@HiltWorker
class TodayReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dashboard: ObserveDashboard,
    private val transactions: TransactionRepository,
    private val preferences: UiPreferences,
    private val notifier: ReminderNotifier,
    private val scheduler: TodayReminderScheduler,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching {
            val snapshot = dashboard.current()
            val all = transactions.observeAll().first()
            // A day ahead: every scheduled payment (and planned spend) due tomorrow.
            if (preferences.reminderPayments.first()) {
                val tomorrow = clock.today().plusDays(1)
                val due = ReminderLines.billsOn(snapshot.upcoming + snapshot.forecastSchedule, tomorrow) +
                    all.filter { BalanceMath.isPlannedUnpaid(it) && it.occurredOn == tomorrow }.map { it.merchantRaw.ifBlank { "Planned spend" } to it.amountMinor }
                if (due.isNotEmpty()) {
                    notifier.today(
                        title = if (due.size == 1) "Due tomorrow: ${due.first().first}" else "${due.size} payments due tomorrow · ${MoneyFormat.format(due.sumOf { it.second }, snapshot.currency)}",
                        lines = due.map { (name, minor) -> "$name  ${MoneyFormat.format(minor, snapshot.currency)}" },
                        id = 7001,
                    )
                }
            }
            // First day of a new cycle: download every statement and check it against the app by hand.
            if (preferences.reminderStatements.first() && CycleReminder.isFirstDay(clock.today(), snapshot.cycleStart)) {
                notifier.newCycle(
                    title = "New cycle: download your bank statements",
                    body = "Download every account's statement for last cycle and review it line by line. Then set up this cycle in Ledgerline.",
                )
            }
            if (preferences.remindToday.first()) {
                val today = clock.today()
                val bills = ReminderLines.billsOn(snapshot.upcoming + snapshot.forecastSchedule, today)
                val planned = all
                    .filter { BalanceMath.isPlannedUnpaid(it) && it.occurredOn == today }
                    .map { it.merchantRaw.ifBlank { "Planned spend" } to it.amountMinor }
                val lines = bills + planned
                if (lines.isNotEmpty()) {
                    val total = lines.sumOf { it.second }
                    notifier.today(
                        title = if (lines.size == 1) "1 payment today" else "${lines.size} payments today · ${MoneyFormat.format(total, snapshot.currency)}",
                        lines = lines.map { (name, minor) -> "$name  ${MoneyFormat.format(minor, snapshot.currency)}" },
                    )
                }
            }
        }
        scheduler.schedule()
        return Result.success()
    }
}

@Singleton
class TodayReminderScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val preferences: UiPreferences,
) {
    /** Sets up the next reminder at your chosen time, replacing any already waiting. */
    suspend fun schedule() {
        val manager = WorkManager.getInstance(context)
        if (!preferences.remindToday.first() && !preferences.reminderPayments.first() && !preferences.reminderStatements.first()) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val now = LocalDateTime.now()
        val delay = Duration.between(now, ReminderTime.next(now, preferences.reminderMinutes.first())).toMillis()
        val request = OneTimeWorkRequestBuilder<TodayReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        manager.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val NAME = "today-reminder"
    }
}
