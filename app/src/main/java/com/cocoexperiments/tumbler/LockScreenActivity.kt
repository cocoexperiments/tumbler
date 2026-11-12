package com.cocoexperiments.tumbler

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
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
 * Shown in front of a locked app (the package in [LockLauncher.EXTRA_TARGET]); hosts the shuffling
 * keypad and the optional fingerprint prompt. A correct PIN records a grant for that app and
 * finishes, which brings the app back to the front. (FragmentActivity because BiometricPrompt
 * requires it.)
 */
class LockScreenActivity : FragmentActivity() {

    private val viewModel: LockViewModel by viewModels()
    private var fingerprintShowing = false

    /** The user dismissed the prompt ("Use PIN") during this attempt; do not pop it up again until the next one. */
    private var fingerprintDeclined = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the lock UI from screenshots, screen recording and the recents thumbnail.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        hideNavigationBar()

        // Back goes Home. It must never reveal the locked app underneath.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.events.collect { event ->
                    when (event) {
                        LockEvent.Rejected -> Haptics.error(this@LockScreenActivity)
                        LockEvent.Unlocked -> finishAndRemoveTask()
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
                    onSelectMinutes = viewModel::selectGrantMinutes,
                )
            }
        }
        startAttempt(intent)
    }

    /** Re-launched while alive: a different locked app came forward, or the preview was opened again. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val target = intent.getStringExtra(LockLauncher.EXTRA_TARGET)
        // Same app again must not reset digits or reshuffle while the user is typing.
        if (target != null && target == LockState.target) return
        setIntent(intent)
        startAttempt(intent)
    }

    private fun startAttempt(intent: Intent) {
        val target = intent.getStringExtra(LockLauncher.EXTRA_TARGET)
        LockState.target = target
        fingerprintDeclined = false
        viewModel.setTarget(target)
    }

    /** Coming back from a stopped state (screen was off): reshuffle and reset the sequence. */
    override fun onRestart() {
        super.onRestart()
        fingerprintDeclined = false
        viewModel.newAttempt()
    }

    /** The system re-shows the bars after dialogs; hide them again. */
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

    /**
     * Left via Home, Recents or another app while the screen is on: remove the lock; the guard shows
     * it again the next time the locked app is opened. If the screen turned off, keep it so it is
     * still in front when the phone wakes.
     */
    override fun onStop() {
        super.onStop()
        if (fingerprintShowing) biometricPrompt.cancelAuthentication() // no prompt over a stopped lock screen
        if (!isFinishing && getSystemService(PowerManager::class.java).isInteractive) finishAndRemoveTask()
    }

    override fun onDestroy() {
        LockState.isForeground = false
        LockState.target = null
        super.onDestroy()
    }

    /** Back or Home from the lock: go to the launcher, never to the locked app. */
    private fun leave() {
        if (LockState.target != null) {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finishAndRemoveTask()
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
     * Offers the fingerprint when it is enabled, enrolled, allowed for this app's mode and the PIN
     * has not been forced (reboot). Without [force] it appears once per attempt; the "Use
     * fingerprint" button passes force = true. The PIN keypad is always there underneath, so the
     * fingerprint can never lock the user out.
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
}
