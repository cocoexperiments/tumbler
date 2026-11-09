package com.cocoexperiments.tumbler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cocoexperiments.tumbler.data.PinRepository

/** Restarts the lock service after reboot or app update. A reboot always starts locked. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repository = PinRepository.get(context)
        if (!repository.hasPin() || !repository.lockEnabled) return
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) LockState.arm(context)
        LockScreenService.start(context)
    }
}
