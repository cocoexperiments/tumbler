package com.cocoexperiments.tumbler

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
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
import com.cocoexperiments.tumbler.util.Biometrics
import com.cocoexperiments.tumbler.util.Haptics
import kotlinx.coroutines.launch

/**
 * Renders above the system keyguard (showWhenLocked) and hosts the dynamic keypad, plus the optional
 * fingerprint prompt. (FragmentActivity because BiometricPrompt requires it.)
 */
class LockScreenActivity : FragmentActivity() {

    private val viewModel: LockViewModel by viewModels()
    private var fingerprintShowing = false

    /** The user dismissed the prompt ("Use PIN") during this attempt; do not pop it up again until the next one. */
    private var fingerprintDeclined = false

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
                LockScreen(
                    state = state,
                    onGroupTapped = viewModel::onGroupTapped,
                    onUseFingerprint = { showFingerprint(force = true) },
                )
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
        newAttempt()
    }

    /** Coming back from a stopped state (screen was off): reshuffle and reset the sequence. */
    override fun onRestart() {
        super.onRestart()
        newAttempt()
    }

    private fun newAttempt() {
        fingerprintDeclined = false
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
        showFingerprint(force = false)
    }

    override fun onPause() {
        LockState.isForeground = false
        super.onPause()
    }

    /** Left via Home, Recents or another app while still locked: come straight back. */
    override fun onStop() {
        super.onStop()
        if (fingerprintShowing) biometricPrompt.cancelAuthentication() // no prompt over a stopped lock screen
        if (!isFinishing) LockLauncher.reassert(this)
    }

    override fun onDestroy() {
        LockState.isForeground = false
        LockState.isActive = false
        super.onDestroy()
    }

    private val biometricPrompt: BiometricPrompt by lazy {
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    fingerprintShowing = false
                    viewModel.onFingerprintSucceeded(Biometrics.isReady(this@LockScreenActivity))
                }

                // Cancelled, "Use PIN", too many tries or sensor lockout: fall back to the PIN keypad.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    fingerprintShowing = false
                    fingerprintDeclined = true
                }
            },
        )
    }

    /**
     * Offers the fingerprint when it is enabled, enrolled and the PIN has not been forced (reboot).
     * Without [force] it appears once per attempt; the "Use fingerprint" button passes force = true.
     * The PIN keypad is always there underneath, so the fingerprint can never lock the user out.
     */
    private fun showFingerprint(force: Boolean) {
        val allowed = viewModel.fingerprintAllowed(Biometrics.isReady(this))
        viewModel.setFingerprintAvailable(allowed)
        if (!allowed || fingerprintShowing || (!force && fingerprintDeclined)) return
        fingerprintShowing = true
        fingerprintDeclined = false
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.fingerprint_title))
                .setSubtitle(getString(R.string.fingerprint_subtitle))
                .setNegativeButtonText(getString(R.string.fingerprint_use_pin))
                .setAllowedAuthenticators(Biometrics.AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build(),
        )
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
