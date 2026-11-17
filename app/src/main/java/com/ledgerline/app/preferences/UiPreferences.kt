package com.ledgerline.app.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ledgerline.app.domain.model.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiPreferences @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val theme: Flow<ThemeChoice> = store.data.map { prefs ->
        runCatching { ThemeChoice.valueOf(prefs[Keys.THEME] ?: ThemeChoice.SYSTEM.name) }
            .getOrDefault(ThemeChoice.SYSTEM)
    }

    val screenshotProtection: Flow<Boolean> = store.data.map { it[Keys.SCREENSHOT] == true }

    val sampleData: Flow<Boolean> = store.data.map { it[Keys.SAMPLE] == true }

    val resetOnLaunch: Flow<Boolean> = store.data.map { it[Keys.RESET_ON_LAUNCH] == true }

    val notificationDetection: Flow<Boolean> = store.data.map { it[Keys.NOTIFICATIONS] == true }

    /** Add captured spends straight to the ledger when the card or account ending matches one account. Off by default. */
    val captureAutoAdd: Flow<Boolean> = store.data.map { it[Keys.CAPTURE_AUTO_ADD] == true }

    /** Post a "tap to add" notice when a spend is captured. On by default once capture is enabled. */
    val captureNotify: Flow<Boolean> = store.data.map { it[Keys.CAPTURE_NOTIFY] != false }

    /** Read new SMS through Android's incoming-SMS broadcast. Needs the receive-SMS permission. Off by default. */
    val captureSmsDirect: Flow<Boolean> = store.data.map { it[Keys.CAPTURE_SMS_DIRECT] == true }

    /** How many notifications from chosen apps Android hid as sensitive. Shown so the person knows why nothing arrives. */
    val hiddenNotices: Flow<Long> = store.data.map { it[Keys.HIDDEN_NOTICES] ?: 0L }

    suspend fun setCaptureSmsDirect(enabled: Boolean) {
        store.edit { it[Keys.CAPTURE_SMS_DIRECT] = enabled }
    }

    suspend fun countHiddenNotice() {
        store.edit { it[Keys.HIDDEN_NOTICES] = (it[Keys.HIDDEN_NOTICES] ?: 0L) + 1 }
    }

    val captureTipDismissed: Flow<Boolean> = store.data.map { it[Keys.CAPTURE_TIP_DISMISSED] == true }

    suspend fun setCaptureAutoAdd(enabled: Boolean) {
        store.edit { it[Keys.CAPTURE_AUTO_ADD] = enabled }
    }

    suspend fun setCaptureNotify(enabled: Boolean) {
        store.edit { it[Keys.CAPTURE_NOTIFY] = enabled }
    }

    suspend fun dismissCaptureTip() {
        store.edit { it[Keys.CAPTURE_TIP_DISMISSED] = true }
    }

    val appLockEnabled: Flow<Boolean> = store.data.map { it[Keys.APP_LOCK] == true }

    val lockTimeoutMinutes: Flow<Int> = store.data.map { (it[Keys.LOCK_TIMEOUT] ?: 1).toInt().coerceIn(1, 30) }

    val pinVerifier: Flow<String?> = store.data.map { it[Keys.PIN] }

    /** A reminder a day before each scheduled payment. On unless you turn it off. */
    val reminderPayments: Flow<Boolean> = store.data.map { it[Keys.REMIND_PAYMENTS] != false }

    /**
     * On the first day of each cycle: a reminder to download every bank statement and review it by hand, which opens
     * the new-cycle checklist. On unless you turn it off.
     */
    val reminderStatements: Flow<Boolean> = store.data.map { it[Keys.REMIND_STATEMENTS] != false }

    suspend fun setReminderStatements(enabled: Boolean) {
        store.edit { it[Keys.REMIND_STATEMENTS] = enabled }
    }

    /** Cycles (by first day, as text) that start with fresh balances. */
    val freshBalanceCycles: Flow<Set<String>> = store.data.map { it[Keys.FRESH_CYCLES].orEmpty() }

    suspend fun setFreshBalanceCycle(cycleStart: java.time.LocalDate, fresh: Boolean) {
        store.edit { prefs ->
            val now = prefs[Keys.FRESH_CYCLES].orEmpty()
            prefs[Keys.FRESH_CYCLES] = if (fresh) now + cycleStart.toString() else now - cycleStart.toString()
        }
    }

    /** The cycle (by its first day) whose set-up card you hid on Home. */
    val cycleCardHiddenFor: Flow<String?> = store.data.map { it[Keys.CYCLE_CARD_HIDDEN] }

    suspend fun hideCycleCard(cycleStart: java.time.LocalDate) {
        store.edit { it[Keys.CYCLE_CARD_HIDDEN] = cycleStart.toString() }
    }

    val reminderBuffer: Flow<Boolean> = store.data.map { it[Keys.REMIND_BUFFER] == true }

    val reminderBudget: Flow<Boolean> = store.data.map { it[Keys.REMIND_BUDGET] == true }

    val reminderAnomaly: Flow<Boolean> = store.data.map { it[Keys.REMIND_ANOMALY] == true }

    val reminderSubscription: Flow<Boolean> = store.data.map { it[Keys.REMIND_SUBSCRIPTION] == true }

    /** The last view chosen on Home. This is a display choice only. */
    val homeView: Flow<com.ledgerline.app.finance.dashboard.HomeView> = store.data.map { prefs ->
        runCatching { com.ledgerline.app.finance.dashboard.HomeView.valueOf(prefs[Keys.HOME_VIEW] ?: "ACTUAL") }
            .getOrDefault(com.ledgerline.app.finance.dashboard.HomeView.ACTUAL)
    }

    suspend fun setHomeView(view: com.ledgerline.app.finance.dashboard.HomeView) {
        store.edit { it[Keys.HOME_VIEW] = view.name }
    }

    /** The pay you split into envelopes, if you typed one. Null means use the income on your calendar. */
    val payIncomeMinor: Flow<Long?> = store.data.map { prefs -> prefs[Keys.PAY_INCOME]?.takeIf { it > 0 } }

    suspend fun setPayIncome(minor: Long?) {
        store.edit { prefs -> if (minor == null || minor <= 0) prefs.remove(Keys.PAY_INCOME) else prefs[Keys.PAY_INCOME] = minor }
    }

    /** How Home groups your money: by envelope (true) or by account (false). A display choice only. */
    val homeByAccount: Flow<Boolean> = store.data.map { it[Keys.HOME_BY_ACCOUNT] == true }

    suspend fun setHomeByAccount(byAccount: Boolean) {
        store.edit { it[Keys.HOME_BY_ACCOUNT] = byAccount }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        store.edit { it[Keys.THEME] = choice.name }
    }

    suspend fun setScreenshotProtection(enabled: Boolean) {
        store.edit { it[Keys.SCREENSHOT] = enabled }
    }

    suspend fun setSampleData(enabled: Boolean) {
        store.edit { it[Keys.SAMPLE] = enabled }
    }

    suspend fun setResetOnLaunch(enabled: Boolean) {
        store.edit { it[Keys.RESET_ON_LAUNCH] = enabled }
    }

    suspend fun setNotificationDetection(enabled: Boolean) {
        store.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setAppLock(enabled: Boolean) {
        store.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setLockTimeoutMinutes(minutes: Int) {
        store.edit { it[Keys.LOCK_TIMEOUT] = minutes.coerceIn(1, 30).toLong() }
    }

    suspend fun setPinVerifier(value: String?) {
        store.edit {
            if (value == null) it.remove(Keys.PIN) else it[Keys.PIN] = value
        }
    }

    /** A notification each day listing that day's payments. On unless you turn it off. */
    val remindToday: Flow<Boolean> = store.data.map { it[Keys.REMIND_TODAY] != false }

    /** When that notification comes, in minutes after midnight. 9:00 by default. */
    val reminderMinutes: Flow<Int> = store.data.map { (it[Keys.REMIND_TIME] ?: 540L).toInt() }

    suspend fun setRemindToday(enabled: Boolean) {
        store.edit { it[Keys.REMIND_TODAY] = enabled }
    }

    suspend fun setReminderMinutes(minutes: Int) {
        store.edit { it[Keys.REMIND_TIME] = minutes.coerceIn(0, 24 * 60 - 1).toLong() }
    }

    suspend fun setReminder(kind: String, enabled: Boolean) {
        val key = when (kind) {
            "payments" -> Keys.REMIND_PAYMENTS
            "buffer" -> Keys.REMIND_BUFFER
            "budget" -> Keys.REMIND_BUDGET
            "anomaly" -> Keys.REMIND_ANOMALY
            else -> Keys.REMIND_SUBSCRIPTION
        }
        store.edit { it[key] = enabled }
    }

    suspend fun recordMaintenance(safeToSpendMinor: Long, currency: String, atEpochMillis: Long) {
        store.edit {
            it[Keys.LAST_MAINTENANCE] = atEpochMillis
            it[Keys.LAST_SAFE_MINOR] = safeToSpendMinor
            it[Keys.LAST_SAFE_CURRENCY] = currency
        }
    }

    private object Keys {
        val REMIND_STATEMENTS = booleanPreferencesKey("remind_statements")
        val CYCLE_CARD_HIDDEN = stringPreferencesKey("cycle_card_hidden")
        val FRESH_CYCLES = androidx.datastore.preferences.core.stringSetPreferencesKey("fresh_balance_cycles")
        val THEME = stringPreferencesKey("theme")
        val HOME_VIEW = stringPreferencesKey("home_view")
        val PAY_INCOME = longPreferencesKey("pay_income_minor")
        val HOME_BY_ACCOUNT = androidx.datastore.preferences.core.booleanPreferencesKey("home_by_account")
        val SCREENSHOT = booleanPreferencesKey("screenshot_protection")
        val SAMPLE = booleanPreferencesKey("sample_data")
        val RESET_ON_LAUNCH = booleanPreferencesKey("debug_reset_on_launch")
        val LAST_MAINTENANCE = longPreferencesKey("last_maintenance")
        val LAST_SAFE_MINOR = longPreferencesKey("last_safe_minor")
        val LAST_SAFE_CURRENCY = stringPreferencesKey("last_safe_currency")
        val NOTIFICATIONS = booleanPreferencesKey("notification_detection")
        val CAPTURE_AUTO_ADD = booleanPreferencesKey("capture_auto_add")
        val CAPTURE_NOTIFY = booleanPreferencesKey("capture_notify")
        val CAPTURE_TIP_DISMISSED = booleanPreferencesKey("capture_tip_dismissed")
        val CAPTURE_SMS_DIRECT = booleanPreferencesKey("capture_sms_direct")
        val HIDDEN_NOTICES = longPreferencesKey("capture_hidden_notices")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val LOCK_TIMEOUT = longPreferencesKey("lock_timeout_minutes")
        val PIN = stringPreferencesKey("pin_verifier")
        val REMIND_PAYMENTS = booleanPreferencesKey("remind_payments")
        val REMIND_TODAY = booleanPreferencesKey("remind_today")
        val REMIND_TIME = longPreferencesKey("remind_time_minutes")
        val REMIND_BUFFER = booleanPreferencesKey("remind_buffer")
        val REMIND_BUDGET = booleanPreferencesKey("remind_budget")
        val REMIND_ANOMALY = booleanPreferencesKey("remind_anomaly")
        val REMIND_SUBSCRIPTION = booleanPreferencesKey("remind_subscription")
    }
}
