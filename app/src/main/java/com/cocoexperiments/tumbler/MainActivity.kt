package com.cocoexperiments.tumbler

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocoexperiments.tumbler.service.LockLauncher
import com.cocoexperiments.tumbler.ui.main.HomeScreen
import com.cocoexperiments.tumbler.ui.main.LockedAppsScreen
import com.cocoexperiments.tumbler.ui.main.MainViewModel
import com.cocoexperiments.tumbler.ui.main.PinSetupScreen
import com.cocoexperiments.tumbler.ui.theme.Brand
import com.cocoexperiments.tumbler.ui.theme.TumblerTheme
import com.cocoexperiments.tumbler.util.AppPermissions
import com.cocoexperiments.tumbler.util.Biometrics

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            TumblerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var changingPin by rememberSaveable { mutableStateOf(false) }
                var pickingApps by rememberSaveable { mutableStateOf(false) }
                val apps by viewModel.apps.collectAsStateWithLifecycle()
                val context = LocalContext.current

                // First launch (no PIN yet) or "Change PIN": registration flow. Otherwise the dashboard.
                val showSetup = !state.hasPin || changingPin
                AnimatedContent(
                    targetState = showSetup,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "mainContent",
                ) { setup ->
                    if (setup) {
                        PinSetupScreen(
                            requireCurrentPin = state.hasPin,
                            verifyCurrentPin = viewModel::verifyCurrentPin,
                            onPinSet = { pin ->
                                viewModel.savePin(pin)
                                changingPin = false
                            },
                            onCancel = if (state.hasPin) ({ changingPin = false }) else null,
                        )
                    } else if (pickingApps) {
                        LaunchedEffect(Unit) { viewModel.loadApps() }
                        LockedAppsScreen(
                            apps = apps,
                            locked = state.lockedApps,
                            onBack = { pickingApps = false },
                            onToggle = viewModel::setAppLocked,
                            onMode = viewModel::setAppMode,
                            onMinutes = viewModel::setAppMinutes,
                        )
                    } else {
                        HomeScreen(
                            state = state,
                            onToggleLock = viewModel::setLockEnabled,
                            onRequestOverlay = { startActivity(AppPermissions.overlaySettingsIntent(context)) },
                            onOpenApps = { pickingApps = true },
                            onPreview = { LockLauncher.preview(context) },
                            onChangePin = { changingPin = true },
                            onToggleFingerprint = viewModel::setFingerprintEnabled,
                            onEnrollFingerprint = { startActivity(Biometrics.enrollIntent()) },
                            onEnableGuard = { startActivity(AppPermissions.accessibilitySettingsIntent()) },
                            onOpenWebsite = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Brand.COMPANY_URL))) },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh() // permissions may have changed in system Settings
    }
}
