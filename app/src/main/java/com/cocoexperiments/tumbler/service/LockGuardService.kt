package com.cocoexperiments.tumbler.service

import android.accessibilityservice.AccessibilityService
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent

/**
 * Optional guard. While the lock is armed, any window change from a package other than Tumbler
 * (the launcher's Home/Recents, another app, Settings...) brings the lock screen back.
 *
 * It only listens to TYPE_WINDOW_STATE_CHANGED and has canRetrieveWindowContent=false, so it cannot
 * read what is on screen. The phone app is allowed through so calls can still be answered.
 */
class LockGuardService : AccessibilityService() {

    private val allowedPackages: Set<String> by lazy {
        buildSet {
            add(packageName)
            add("com.android.server.telecom")
            add("com.android.incallui")
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { add(it) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LockState.load(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!LockState.armed) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in allowedPackages) return
        LockLauncher.reassert(this)
    }

    override fun onInterrupt() = Unit
}
