package com.cocoexperiments.tumbler.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cocoexperiments.tumbler.TumblerApp
import com.cocoexperiments.tumbler.LockScreenActivity
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.util.AppPermissions

/**
 * Two ways to put [LockScreenActivity] on screen from the background:
 *  1. startActivity() - allowed for apps holding SYSTEM_ALERT_WINDOW.
 *  2. A full-screen-intent notification - the system launches it over the keyguard.
 */
object LockLauncher {
    const val EXTRA_WAKE_SCREEN = "extra_wake_screen"
    const val NOTIFICATION_ID = 2001

    /**
     * Called on SCREEN_OFF. The activity is created now so it is already waiting above the keyguard
     * when the user next wakes the phone. It must NOT turn the screen on, or the phone would wake
     * itself right after sleeping.
     */
    fun prelaunch(context: Context) {
        if (AppPermissions.canDrawOverlays(context)) start(context, wakeScreen = false)
    }

    /** Called on SCREEN_ON when no lock UI is showing, and by the in-app preview. */
    fun showNow(context: Context) {
        if (AppPermissions.canDrawOverlays(context)) start(context, wakeScreen = true)
        postFullScreenNotification(context)
    }

    private fun intent(context: Context, wakeScreen: Boolean) =
        Intent(context, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_WAKE_SCREEN, wakeScreen)

    private fun start(context: Context, wakeScreen: Boolean) {
        runCatching { context.startActivity(intent(context, wakeScreen)) }
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    private fun postFullScreenNotification(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent(context, wakeScreen = true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, TumblerApp.CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(context.getString(R.string.notif_launch_title))
            .setContentText(context.getString(R.string.notif_launch_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
