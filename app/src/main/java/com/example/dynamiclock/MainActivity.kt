package com.example.dynamiclock

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dynamiclock.service.LockLauncher
import com.example.dynamiclock.ui.main.HomeScreen
import com.example.dynamiclock.ui.main.MainViewModel
import com.example.dynamiclock.ui.main.PinSetupScreen
import com.example.dynamiclock.ui.theme.DynamicLockTheme
import com.example.dynamiclock.util.AppPermissions

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DynamicLockTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var changingPin by rememberSaveable { mutableStateOf(false) }
                val context = LocalContext.current

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { viewModel.refresh() }

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
                    } else {
                        HomeScreen(
                            state = state,
                            onToggleLock = viewModel::setLockEnabled,
                            onRequestOverlay = { startActivity(AppPermissions.overlaySettingsIntent(context)) },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onRequestFullScreen = { startActivity(AppPermissions.fullScreenIntentSettingsIntent(context)) },
                            onPreview = { LockLauncher.showNow(context) },
                            onChangePin = { changingPin = true },
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
