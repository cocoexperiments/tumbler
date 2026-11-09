package com.cocoexperiments.tumbler.service

import android.content.Context
import com.cocoexperiments.tumbler.data.PinRepository

/**
 * Process-wide lock state.
 *
 * - [armed]: the phone is locked as far as Tumbler is concerned. Set on screen-off and boot, cleared
 *   only by a correct PIN (or by disabling the lock). Persisted, so it survives process death.
 * - [isActive]: a LockScreenActivity instance exists.
 * - [isForeground]: that activity is resumed (on screen, in front).
 */
object LockState {
    @Volatile
    var isActive: Boolean = false

    @Volatile
    var isForeground: Boolean = false

    @Volatile
    var armed: Boolean = false
        private set

    /** Reads the persisted flag; call when a component starts in a fresh process. */
    fun load(context: Context) {
        armed = PinRepository.get(context).lockArmed
    }

    fun arm(context: Context) {
        armed = true
        PinRepository.get(context).lockArmed = true
    }

    fun disarm(context: Context) {
        armed = false
        PinRepository.get(context).lockArmed = false
    }
}
