package com.cocoexperiments.tumbler.domain

/** How a locked app behaves when it is opened. */
enum class LockMode {
    /** PIN (or fingerprint) every time. [LockedApp.minutes] is the grace window before it asks again. */
    PRIVACY,

    /** A mandatory pause, then the PIN, then a time-boxed window of [LockedApp.minutes] before it locks again. */
    WELLBEING,
}

/** One app the user has put behind Tumbler. */
data class LockedApp(
    val packageName: String,
    val mode: LockMode = LockMode.PRIVACY,
    /** Privacy: grace window. Wellbeing: default length of the unlocked window. */
    val minutes: Int = AppLockPolicy.DEFAULT_GRACE_MINUTES,
)

/** Pure rules for per-app locking. No Android dependencies, so it is unit tested. */
object AppLockPolicy {
    /** Wellbeing: seconds the keypad stays disabled after the lock appears. */
    const val PAUSE_SECONDS = 10

    const val DEFAULT_GRACE_MINUTES = 1
    const val DEFAULT_WELLBEING_MINUTES = 10

    val GRACE_OPTIONS = listOf(1, 5, 15)
    val WELLBEING_OPTIONS = listOf(5, 10, 15, 30)

    fun options(mode: LockMode): List<Int> = if (mode == LockMode.PRIVACY) GRACE_OPTIONS else WELLBEING_OPTIONS

    /** Sensible default for [minutes] when an app is first locked or its mode changes. */
    fun defaultMinutes(mode: LockMode): Int =
        if (mode == LockMode.PRIVACY) DEFAULT_GRACE_MINUTES else DEFAULT_WELLBEING_MINUTES

    /**
     * True when opening [app] must show the lock now. [unlockedUntil] is when the current grant ends
     * (same clock as [now]), or null when there is none.
     */
    fun shouldLock(app: LockedApp?, unlockedUntil: Long?, now: Long): Boolean =
        app != null && (unlockedUntil == null || now >= unlockedUntil)

    fun pauseSeconds(mode: LockMode): Int = if (mode == LockMode.WELLBEING) PAUSE_SECONDS else 0

    /** When a grant of [minutes] given at [now] ends. */
    fun grantExpiry(now: Long, minutes: Int): Long = now + minutes * 60_000L

    /** The fingerprint skips the pause, so it is only for Privacy apps. */
    fun fingerprintAllowedFor(mode: LockMode): Boolean = mode == LockMode.PRIVACY

    /** Privacy apps re-lock when the screen turns off; Wellbeing windows keep running. */
    fun clearsOnScreenOff(mode: LockMode): Boolean = mode == LockMode.PRIVACY

    // Storage format: "package|MODE|minutes". Unknown or damaged entries are dropped.
    fun encode(app: LockedApp): String = "${app.packageName}|${app.mode.name}|${app.minutes}"

    fun decode(raw: String): LockedApp? {
        val parts = raw.split('|')
        if (parts.size != 3 || parts[0].isBlank()) return null
        val mode = LockMode.entries.firstOrNull { it.name == parts[1] } ?: return null
        val minutes = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return LockedApp(parts[0], mode, minutes)
    }
}
