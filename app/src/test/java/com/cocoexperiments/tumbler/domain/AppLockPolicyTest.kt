package com.cocoexperiments.tumbler.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private val insta = LockedApp("com.example.insta", LockMode.WELLBEING, 10)

    @Test
    fun unlockedAppsNeverLock() {
        assertFalse(AppLockPolicy.shouldLock(null, unlockedUntil = null, now = 1_000L))
    }

    @Test
    fun lockedAppWithoutGrantLocks() {
        assertTrue(AppLockPolicy.shouldLock(insta, unlockedUntil = null, now = 1_000L))
    }

    @Test
    fun grantKeepsAppOpenUntilItExpires() {
        assertFalse(AppLockPolicy.shouldLock(insta, unlockedUntil = 5_000L, now = 4_999L))
        assertTrue(AppLockPolicy.shouldLock(insta, unlockedUntil = 5_000L, now = 5_000L))
        assertTrue(AppLockPolicy.shouldLock(insta, unlockedUntil = 5_000L, now = 9_000L))
    }

    @Test
    fun grantExpiryAddsMinutes() {
        assertEquals(1_000L + 10 * 60_000L, AppLockPolicy.grantExpiry(1_000L, 10))
    }

    @Test
    fun onlyWellbeingHasAPause() {
        assertEquals(AppLockPolicy.PAUSE_SECONDS, AppLockPolicy.pauseSeconds(LockMode.WELLBEING))
        assertEquals(0, AppLockPolicy.pauseSeconds(LockMode.PRIVACY))
    }

    @Test
    fun fingerprintCannotSkipTheWellbeingPause() {
        assertTrue(AppLockPolicy.fingerprintAllowedFor(LockMode.PRIVACY))
        assertFalse(AppLockPolicy.fingerprintAllowedFor(LockMode.WELLBEING))
    }

    @Test
    fun onlyPrivacyGrantsClearOnScreenOff() {
        assertTrue(AppLockPolicy.clearsOnScreenOff(LockMode.PRIVACY))
        assertFalse(AppLockPolicy.clearsOnScreenOff(LockMode.WELLBEING))
    }

    @Test
    fun defaultsAreOffered() {
        for (mode in LockMode.entries) {
            assertTrue(AppLockPolicy.defaultMinutes(mode) in AppLockPolicy.options(mode))
        }
    }

    @Test
    fun encodeDecodeRoundTrips() {
        assertEquals(insta, AppLockPolicy.decode(AppLockPolicy.encode(insta)))
    }

    @Test
    fun decodeRejectsDamagedEntries() {
        assertNull(AppLockPolicy.decode(""))
        assertNull(AppLockPolicy.decode("pkg|PRIVACY"))
        assertNull(AppLockPolicy.decode("pkg|NOPE|5"))
        assertNull(AppLockPolicy.decode("pkg|PRIVACY|abc"))
        assertNull(AppLockPolicy.decode("pkg|PRIVACY|0"))
        assertNull(AppLockPolicy.decode("|PRIVACY|5"))
    }
}
