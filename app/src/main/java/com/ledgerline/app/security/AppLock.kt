package com.ledgerline.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ledgerline.app.preferences.UiPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLock @Inject constructor(
    private val preferences: UiPreferences,
) {
    private val unlocked = MutableStateFlow(false)
    private var backgroundAtMillis: Long? = null

    val locked: Flow<Boolean> = combine(preferences.appLockEnabled, unlocked) { enabled, open -> enabled && !open }

    fun unlock() {
        unlocked.value = true
    }

    fun lock() {
        unlocked.value = false
    }

    fun onBackground(nowMillis: Long) {
        backgroundAtMillis = nowMillis
    }

    suspend fun onForeground(nowMillis: Long) {
        val timeoutMinutes = preferences.lockTimeoutMinutes.first()
        val started = backgroundAtMillis ?: return
        if (nowMillis - started >= timeoutMinutes * 60_000L) lock()
    }
}

@Singleton
class PinStore @Inject constructor(
    private val preferences: UiPreferences,
) {
    suspend fun hasPin(): Boolean = preferences.pinVerifier.first() != null

    suspend fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() })
        preferences.setPinVerifier(mac(pin))
    }

    suspend fun verify(pin: String): Boolean {
        val stored = preferences.pinVerifier.first() ?: return false
        return mac(pin) == stored
    }

    suspend fun clear() {
        preferences.setPinVerifier(null)
    }

    private fun mac(pin: String): String {
        val key = hmacKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmacKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "ledgerline_pin"
    }
}
