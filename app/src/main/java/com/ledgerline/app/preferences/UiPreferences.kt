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

    val notificationDetection: Flow<Boolean> = store.data.map { it[Keys.NOTIFICATIONS] == true }

    val appLockEnabled: Flow<Boolean> = store.data.map { it[Keys.APP_LOCK] == true }

    val lockTimeoutMinutes: Flow<Int> = store.data.map { (it[Keys.LOCK_TIMEOUT] ?: 1).toInt().coerceIn(1, 30) }

    val pinVerifier: Flow<String?> = store.data.map { it[Keys.PIN] }

    val reminderPayments: Flow<Boolean> = store.data.map { it[Keys.REMIND_PAYMENTS] == true }

    val reminderBuffer: Flow<Boolean> = store.data.map { it[Keys.REMIND_BUFFER] == true }

    val reminderBudget: Flow<Boolean> = store.data.map { it[Keys.REMIND_BUDGET] == true }

    val reminderAnomaly: Flow<Boolean> = store.data.map { it[Keys.REMIND_ANOMALY] == true }

    val reminderSubscription: Flow<Boolean> = store.data.map { it[Keys.REMIND_SUBSCRIPTION] == true }

    suspend fun setTheme(choice: ThemeChoice) {
        store.edit { it[Keys.THEME] = choice.name }
    }

    suspend fun setScreenshotProtection(enabled: Boolean) {
        store.edit { it[Keys.SCREENSHOT] = enabled }
    }

    suspend fun setSampleData(enabled: Boolean) {
        store.edit { it[Keys.SAMPLE] = enabled }
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
        val THEME = stringPreferencesKey("theme")
        val SCREENSHOT = booleanPreferencesKey("screenshot_protection")
        val SAMPLE = booleanPreferencesKey("sample_data")
        val LAST_MAINTENANCE = longPreferencesKey("last_maintenance")
        val LAST_SAFE_MINOR = longPreferencesKey("last_safe_minor")
        val LAST_SAFE_CURRENCY = stringPreferencesKey("last_safe_currency")
        val NOTIFICATIONS = booleanPreferencesKey("notification_detection")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val LOCK_TIMEOUT = longPreferencesKey("lock_timeout_minutes")
        val PIN = stringPreferencesKey("pin_verifier")
        val REMIND_PAYMENTS = booleanPreferencesKey("remind_payments")
        val REMIND_BUFFER = booleanPreferencesKey("remind_buffer")
        val REMIND_BUDGET = booleanPreferencesKey("remind_budget")
        val REMIND_ANOMALY = booleanPreferencesKey("remind_anomaly")
        val REMIND_SUBSCRIPTION = booleanPreferencesKey("remind_subscription")
    }
}
