package com.cocoexperiments.tumbler.domain

/** Decides whether the fingerprint may be offered on the lock screen. Pure, so it is unit tested. */
object UnlockPolicy {
    /**
     * @param enabled the user turned fingerprint unlock on in the app
     * @param pinRequired the PIN must be used next (after a reboot), like the system's own lock
     * @param sensorReady a strong biometric is enrolled and usable right now
     */
    fun fingerprintAllowed(enabled: Boolean, pinRequired: Boolean, sensorReady: Boolean): Boolean =
        enabled && !pinRequired && sensorReady
}
