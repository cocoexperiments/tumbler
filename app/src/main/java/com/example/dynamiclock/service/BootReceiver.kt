package com.example.dynamiclock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dynamiclock.data.PinRepository

/** Restarts the lock service after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repository = PinRepository.get(context)
        if (repository.hasPin() && repository.lockEnabled) LockScreenService.start(context)
    }
}
