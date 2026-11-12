package com.cocoexperiments.tumbler.service

import android.content.Context
import android.os.SystemClock
import com.cocoexperiments.tumbler.data.PinRepository
import com.cocoexperiments.tumbler.domain.AppLockPolicy
import com.cocoexperiments.tumbler.domain.LockedApp
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide lock state, cached in memory because the guard is asked about every window change.
 *
 * - [lockedApps] / [enabled]: mirror of the saved settings. Call [reload] after changing them.
 * - grants: per-package "unlocked until" times on the [SystemClock.elapsedRealtime] clock. They live
 *   in memory only, so a killed process simply asks for the PIN again.
 * - [isForeground] / [target]: the lock screen is resumed, and which app it is guarding.
 */
object LockState {
    @Volatile
    var isForeground: Boolean = false

    @Volatile
    var target: String? = null

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    private var lockedApps: Map<String, LockedApp> = emptyMap()

    private val grants = ConcurrentHashMap<String, Long>()

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Reads the saved settings; call when a component starts in a fresh process and after edits. */
    fun reload(context: Context) {
        val repository = PinRepository.get(context)
        enabled = repository.lockEnabled && repository.hasPin()
        lockedApps = repository.lockedApps.associateBy { it.packageName }
        grants.keys.retainAll(lockedApps.keys)
    }

    fun appFor(packageName: String): LockedApp? = lockedApps[packageName]

    /** True when opening [packageName] must show the lock right now. */
    fun needsLock(packageName: String): Boolean =
        enabled && AppLockPolicy.shouldLock(lockedApps[packageName], grants[packageName], now())

    fun grantRemainingMillis(packageName: String): Long =
        ((grants[packageName] ?: 0L) - now()).coerceAtLeast(0L)

    fun grant(packageName: String, minutes: Int) {
        grants[packageName] = AppLockPolicy.grantExpiry(now(), minutes)
    }

    /** Screen turned off: Privacy apps ask for the PIN again; Wellbeing windows keep running. */
    fun onScreenOff() {
        lockedApps.values
            .filter { AppLockPolicy.clearsOnScreenOff(it.mode) }
            .forEach { grants.remove(it.packageName) }
    }
}
