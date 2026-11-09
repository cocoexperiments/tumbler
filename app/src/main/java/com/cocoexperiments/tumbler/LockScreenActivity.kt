package com.cocoexperiments.tumbler

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cocoexperiments.tumbler.service.LockLauncher
import com.cocoexperiments.tumbler.service.LockState
import com.cocoexperiments.tumbler.ui.lock.LockEvent
import com.cocoexperiments.tumbler.ui.lock.LockScreen
import com.cocoexperiments.tumbler.ui.lock.LockViewModel
import com.cocoexperiments.tumbler.ui.theme.TumblerTheme
import com.cocoexperiments.tumbler.util.Haptics
import kotlinx.coroutines.launch

/** Renders above the system keyguard (showWhenLocked) and hosts the dynamic keypad. */
class LockScreenActivity : ComponentActivity() {

    private val viewModel: LockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockState.isActive = true
        configureWindow(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        hideNavigationBar()
        NotificationManagerCompat.from(this).cancel(LockLauncher.NOTIFICATION_ID)

        // Back must not dismiss the lock screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.events.collect { event ->
                    when (event) {
                        LockEvent.Rejected -> Haptics.error(this@LockScreenActivity)
                        LockEvent.Unlocked -> dismissLockScreen()
                    }
                }
            }
        }

        setContent {
            TumblerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LockScreen(state = state, onGroupTapped = viewModel::onGroupTapped)
            }
        }
    }

    /** Re-launched while already alive (e.g. screen turned off and on again): start a new attempt. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A bring-to-front from the guard must not reset digits or reshuffle while the user is typing.
        if (intent.getBooleanExtra(LockLauncher.EXTRA_REASSERT, false)) return
        setIntent(intent)
        applyWake(intent)
        viewModel.newAttempt()
    }

    /** Coming back from a stopped state (screen was off): reshuffle and reset the sequence. */
    override fun onRestart() {
        super.onRestart()
        viewModel.newAttempt()
    }

    /** The system re-shows the bars after dialogs, keyguard prompts, etc.; hide them again. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    /** Hides the back/home/recents buttons; a swipe from the edge only reveals them transiently. */
    private fun hideNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onResume() {
        super.onResume()
        LockState.isForeground = true
    }

    override fun onPause() {
        LockState.isForeground = false
        super.onPause()
    }

    /** Left via Home, Recents or another app while still locked: come straight back. */
    override fun onStop() {
        super.onStop()
        if (!isFinishing) LockLauncher.reassert(this)
    }

    override fun onDestroy() {
        LockState.isForeground = false
        LockState.isActive = false
        super.onDestroy()
    }

    private fun configureWindow(intent: Intent) {
        // Hide the lock UI from screenshots, screen recording and the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        applyWake(intent)
    }

    /** Only wake the display when launched while it is meant to be on; see LockLauncher.prelaunch(). */
    private fun applyWake(intent: Intent) {
        if (!intent.getBooleanExtra(LockLauncher.EXTRA_WAKE_SCREEN, false)) return
        if (Build.VERSION.SDK_INT >= 27) {
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    /**
     * Success path. Third-party apps cannot unlock a secure system keyguard on their own, so we ask
     * the system to dismiss it (no-op without a secure lock; otherwise the system credential prompt
     * appears) and then remove this activity.
     */
    private fun dismissLockScreen() {
        LockState.disarm(this) // before finishing, so onStop() does not relaunch the lock
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = finishAndRemoveTask()
                    override fun onDismissCancelled() = finishAndRemoveTask()
                    override fun onDismissError() = finishAndRemoveTask()
                },
            )
        } else {
            finishAndRemoveTask()
        }
    }
}
