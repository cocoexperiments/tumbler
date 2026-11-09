package com.example.dynamiclock.service

/** Process-wide flag: is a LockScreenActivity instance alive? Lets the service avoid duplicate launches. */
object LockState {
    @Volatile
    var isActive: Boolean = false
}
