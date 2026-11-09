package com.cocoexperiments.tumbler.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockPolicyTest {
    @Test
    fun allowedOnlyWhenEnabledReadyAndNoPinRequired() {
        assertTrue(UnlockPolicy.fingerprintAllowed(enabled = true, pinRequired = false, sensorReady = true))
    }

    @Test
    fun notAllowedWhenDisabled() {
        assertFalse(UnlockPolicy.fingerprintAllowed(enabled = false, pinRequired = false, sensorReady = true))
    }

    @Test
    fun notAllowedWhenPinRequiredAfterReboot() {
        assertFalse(UnlockPolicy.fingerprintAllowed(enabled = true, pinRequired = true, sensorReady = true))
    }

    @Test
    fun notAllowedWhenSensorNotReady() {
        assertFalse(UnlockPolicy.fingerprintAllowed(enabled = true, pinRequired = false, sensorReady = false))
    }
}
