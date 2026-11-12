package com.cocoexperiments.tumbler.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.cocoexperiments.tumbler.LockScreenActivity
import com.cocoexperiments.tumbler.util.AppPermissions

/**
 * Puts [LockScreenActivity] in front of a locked app. Starting an activity from the background is
 * allowed because Tumbler holds SYSTEM_ALERT_WINDOW.
 */
object LockLauncher {
    const val EXTRA_TARGET = "extra_target"
    private const val DEBOUNCE_MS = 400L

    @Volatile
    private var lastShown = 0L

    /**
     * Shows the lock for [targetPackage]. No-op while the lock for that same app is already on
     * screen. Debounced so repeated window events cannot cause a launch loop.
     */
    fun show(context: Context, targetPackage: String) {
        if (LockState.isForeground && LockState.target == targetPackage) return
        if (!AppPermissions.canDrawOverlays(context)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShown < DEBOUNCE_MS) return
        lastShown = now
        start(context, targetPackage)
    }

    /** In-app preview: the lock screen without a target app. */
    fun preview(context: Context) = start(context, null)

    private fun start(context: Context, targetPackage: String?) {
        val intent = Intent(context, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        targetPackage?.let { intent.putExtra(EXTRA_TARGET, it) }
        runCatching { context.startActivity(intent) }
    }
}
