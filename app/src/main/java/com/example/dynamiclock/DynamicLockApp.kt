package com.example.dynamiclock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.dynamiclock.data.PinRepository
import kotlin.concurrent.thread

class DynamicLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
        // Keystore + encrypted prefs initialisation is slow on first use; warm it off the main thread
        // so the lock screen opens instantly.
        thread(name = "pin-prefs-warmup") { PinRepository.get(this).hasPin() }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, getString(R.string.channel_service_name), NotificationManager.IMPORTANCE_LOW)
                .apply { lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LAUNCH, getString(R.string.channel_launch_name), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
                    setSound(null, null)
                    enableVibration(false)
                },
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "lock_service"
        const val CHANNEL_LAUNCH = "lock_launch"
    }
}
