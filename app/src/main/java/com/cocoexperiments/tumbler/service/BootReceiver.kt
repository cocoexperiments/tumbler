package com.cocoexperiments.tumbler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cocoexperiments.tumbler.data.PinRepository

/** After a reboot the first unlock must be by PIN, like the system's own lock. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = PinRepository.get(context)
        if (repository.hasPin() && repository.lockEnabled) repository.pinRequired = true
    }
}
