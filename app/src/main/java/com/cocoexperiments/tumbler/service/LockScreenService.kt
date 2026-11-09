package com.cocoexperiments.tumbler.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cocoexperiments.tumbler.TumblerApp
import com.cocoexperiments.tumbler.MainActivity
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.data.PinRepository

/**
 * Foreground service that listens for ACTION_SCREEN_OFF / ACTION_SCREEN_ON.
 * These broadcasts cannot be declared in the manifest, so the receiver is registered at runtime.
 */
class LockScreenService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LockState.arm(context)
                    LockLauncher.prelaunch(context)
                }
                Intent.ACTION_SCREEN_ON -> if (LockState.armed && !LockState.isActive) LockLauncher.showNow(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LockState.load(this)
        // Must be called promptly after startForegroundService().
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Restarted (boot, crash, OEM kill) while still armed: put the lock back.
        LockLauncher.reassert(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = PinRepository.get(this)
        if (intent?.action == ACTION_STOP || !repository.hasPin() || !repository.lockEnabled) {
            LockState.disarm(this)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /** The app was swiped out of Recents. The service normally survives; if still armed, bring the lock back. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        LockLauncher.reassert(this)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() =
        NotificationCompat.Builder(this, TumblerApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.cocoexperiments.tumbler.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LockScreenService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockScreenService::class.java))
        }
    }
}
