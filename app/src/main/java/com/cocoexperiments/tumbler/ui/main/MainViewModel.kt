package com.cocoexperiments.tumbler.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cocoexperiments.tumbler.data.PinRepository
import com.cocoexperiments.tumbler.service.LockScreenService
import com.cocoexperiments.tumbler.service.LockState
import com.cocoexperiments.tumbler.util.AppPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val hasPin: Boolean = false,
    val lockEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val fullScreenGranted: Boolean = false,
    val systemLockSecure: Boolean = false,
    val guardEnabled: Boolean = false,
) {
    val allPermissionsGranted: Boolean get() = overlayGranted && notificationsGranted && fullScreenGranted
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PinRepository.get(application)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads PIN and permission status. Call from onResume: the user may have just returned from Settings. */
    fun refresh() {
        val context = getApplication<Application>()
        _state.update {
            MainUiState(
                hasPin = repository.hasPin(),
                lockEnabled = repository.lockEnabled,
                overlayGranted = AppPermissions.canDrawOverlays(context),
                notificationsGranted = AppPermissions.hasNotifications(context),
                fullScreenGranted = AppPermissions.canUseFullScreenIntent(context),
                systemLockSecure = AppPermissions.isSystemLockSecure(context),
                guardEnabled = AppPermissions.isGuardEnabled(context),
            )
        }
    }

    fun verifyCurrentPin(pin: String): Boolean = repository.verifyPin(pin)

    fun savePin(pin: String) {
        repository.savePin(pin)
        refresh()
    }

    fun setLockEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            if (!_state.value.allPermissionsGranted || !repository.hasPin()) return
            repository.lockEnabled = true
            LockScreenService.start(context)
        } else {
            repository.lockEnabled = false
            LockState.disarm(context)
            LockScreenService.stop(context)
        }
        refresh()
    }
}
