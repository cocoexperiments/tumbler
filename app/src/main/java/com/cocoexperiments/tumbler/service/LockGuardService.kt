package com.cocoexperiments.tumbler.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

/**
 * The trigger for app locking. When a window from a locked app comes to the front and it has no
 * active grant, the lock screen is shown over it.
 *
 * It only listens to TYPE_WINDOW_STATE_CHANGED and has canRetrieveWindowContent=false, so it sees
 * which package is in front and nothing else: it cannot read what is on screen.
 *
 * A grant that runs out while the app is still open (Wellbeing windows) is enforced with a timer.
 */
class LockGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** The last app that came to the front (ignoring Tumbler itself), used when a grant expires. */
    private var foregroundPackage: String? = null

    private val expiryCheck = Runnable {
        foregroundPackage?.let { pkg -> if (LockState.needsLock(pkg)) LockLauncher.show(this, pkg) }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(expiryCheck)
                    LockState.onScreenOff()
                }
                // The locked app may still be in front when the screen comes back.
                Intent.ACTION_SCREEN_ON ->
                    foregroundPackage?.let { pkg -> if (LockState.needsLock(pkg)) LockLauncher.show(context, pkg) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LockState.reload(this)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // our own lock screen or settings
        handler.removeCallbacks(expiryCheck)
        foregroundPackage = pkg
        if (!LockState.enabled || LockState.appFor(pkg) == null) return
        if (LockState.needsLock(pkg)) {
            LockLauncher.show(this, pkg)
        } else {
            handler.postDelayed(expiryCheck, LockState.grantRemainingMillis(pkg) + 50L)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }
}
