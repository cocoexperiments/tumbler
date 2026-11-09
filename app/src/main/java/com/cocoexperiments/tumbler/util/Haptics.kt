package com.cocoexperiments.tumbler.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {

    /** Short triple-pulse used for a wrong tap / wrong PIN. */
    fun error(context: Context) {
        val vibrator = vibrator(context) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60, 50, 120), -1))
    }

    private fun vibrator(context: Context): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }
}
