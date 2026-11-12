package com.cocoexperiments.tumbler.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cocoexperiments.tumbler.domain.AppLockPolicy
import com.cocoexperiments.tumbler.domain.LockedApp
import com.cocoexperiments.tumbler.domain.LockoutPolicy
import com.cocoexperiments.tumbler.domain.PinPolicy
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Stores the master PIN and lock settings in [EncryptedSharedPreferences] (AES256-GCM values,
 * AES256-SIV keys, master key held in the Android Keystore).
 *
 * The PIN is kept as digits rather than a hash because the dynamic keypad must test whether
 * PIN[step] is inside the tapped group.
 */
class PinRepository private constructor(private val appContext: Context) {

    private val prefs: SharedPreferences by lazy { openEncryptedPrefs() }

    fun hasPin(): Boolean = readPin() != null

    fun savePin(pin: String) {
        require(pin.length == PinPolicy.LENGTH && pin.all(Char::isDigit)) { "PIN must be ${PinPolicy.LENGTH} digits" }
        prefs.edit().putString(KEY_PIN, pin).putInt(KEY_FAILURES, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).commit()
    }

    fun readPin(): List<Int>? =
        prefs.getString(KEY_PIN, null)
            ?.takeIf { it.length == PinPolicy.LENGTH && it.all(Char::isDigit) }
            ?.map { it.digitToInt() }

    /** Constant-time comparison. */
    fun verifyPin(candidate: String): Boolean {
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), candidate.toByteArray())
    }

    /** Master switch for app locking. */
    var lockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).commit()
        }

    /** Apps behind Tumbler, with the mode chosen for each. */
    var lockedApps: List<LockedApp>
        get() = (prefs.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet())
            .mapNotNull(AppLockPolicy::decode)
            .sortedBy { it.packageName }
        set(value) {
            prefs.edit().putStringSet(KEY_LOCKED_APPS, value.map(AppLockPolicy::encode).toSet()).commit()
        }

    /** Successful opens of [packageName] today (Wellbeing mode shows this on the pause screen). */
    fun opensToday(packageName: String, today: String = LocalDate.now().toString()): Int {
        val raw = prefs.getString(KEY_OPENS + packageName, null) ?: return 0
        val (day, count) = raw.split(':').takeIf { it.size == 2 } ?: return 0
        return if (day == today) count.toIntOrNull() ?: 0 else 0
    }

    fun recordOpen(packageName: String, today: String = LocalDate.now().toString()) {
        val count = opensToday(packageName, today) + 1
        prefs.edit().putString(KEY_OPENS + packageName, "$today:$count").apply()
    }

    /** The user opted in to unlocking with a fingerprint as an alternative to the PIN. */
    var fingerprintEnabled: Boolean
        get() = prefs.getBoolean(KEY_FINGERPRINT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FINGERPRINT, value).commit()
        }

    /** Set on boot: the PIN must be entered once before the fingerprint works again. Cleared only by a correct PIN. */
    var pinRequired: Boolean
        get() = prefs.getBoolean(KEY_PIN_REQUIRED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PIN_REQUIRED, value).commit()
        }

    /** Registers a wrong tap and starts a lockout when the policy demands one. */
    fun recordFailure() {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val lockout = LockoutPolicy.lockoutMillis(failures)
        prefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_LOCKOUT_UNTIL, if (lockout > 0) System.currentTimeMillis() + lockout else 0L)
            .apply()
    }

    fun clearFailures() {
        prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
    }

    fun lockoutRemainingMillis(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - now).coerceAtLeast(0L)

    private fun openEncryptedPrefs(): SharedPreferences =
        try {
            createPrefs()
        } catch (e: GeneralSecurityException) {
            recoverAndCreate()
        } catch (e: IOException) {
            recoverAndCreate()
        }

    /** Keystore entry and file can fall out of sync (restore, OS upgrade); start clean so the user can re-register. */
    private fun recoverAndCreate(): SharedPreferences {
        appContext.deleteSharedPreferences(FILE_NAME)
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
        return createPrefs()
    }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val FILE_NAME = "tumbler_secure_prefs"
        private const val KEY_PIN = "master_pin"
        private const val KEY_ENABLED = "lock_enabled"
        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_OPENS = "opens_"
        private const val KEY_FINGERPRINT = "fingerprint_enabled"
        private const val KEY_PIN_REQUIRED = "pin_required"
        private const val KEY_FAILURES = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        @Volatile
        private var instance: PinRepository? = null

        fun get(context: Context): PinRepository =
            instance ?: synchronized(this) {
                instance ?: PinRepository(context.applicationContext).also { instance = it }
            }
    }
}
