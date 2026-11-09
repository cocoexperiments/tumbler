package com.cocoexperiments.tumbler.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

/** Fingerprint (strong biometric) availability. Android only allows enrolment when a system screen lock exists. */
object Biometrics {
    const val AUTHENTICATORS = BIOMETRIC_STRONG

    /** A strong biometric is enrolled and ready to use right now. */
    fun isReady(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** The device has the hardware, even if nothing is enrolled yet. */
    fun hasHardware(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS).let {
            it == BiometricManager.BIOMETRIC_SUCCESS || it == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        }

    fun enrollIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
}
