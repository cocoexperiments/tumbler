package com.cocoexperiments.tumbler.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cocoexperiments.tumbler.data.PinRepository
import com.cocoexperiments.tumbler.domain.AppLockPolicy
import com.cocoexperiments.tumbler.domain.LockMode
import com.cocoexperiments.tumbler.domain.LockedApp
import com.cocoexperiments.tumbler.service.LockState
import com.cocoexperiments.tumbler.util.AppEntry
import com.cocoexperiments.tumbler.util.AppPermissions
import com.cocoexperiments.tumbler.util.Biometrics
import com.cocoexperiments.tumbler.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val hasPin: Boolean = false,
    val lockEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val guardEnabled: Boolean = false,
    val fingerprintEnabled: Boolean = false,
    val fingerprintHardware: Boolean = false,
    val fingerprintReady: Boolean = false,
    val lockedApps: Map<String, LockedApp> = emptyMap(),
) {
    /** Everything app locking needs besides the PIN: Tumbler must see which app opens and be able to cover it. */
    val allPermissionsGranted: Boolean get() = overlayGranted && guardEnabled
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PinRepository.get(application)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /** Installed launchable apps; null until loaded. */
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads PIN, permissions and settings. Call from onResume: the user may have just returned from Settings. */
    fun refresh() {
        val context = getApplication<Application>()
        _state.update {
            MainUiState(
                hasPin = repository.hasPin(),
                lockEnabled = repository.lockEnabled,
                overlayGranted = AppPermissions.canDrawOverlays(context),
                guardEnabled = AppPermissions.isGuardEnabled(context),
                fingerprintEnabled = repository.fingerprintEnabled,
                fingerprintHardware = Biometrics.hasHardware(context),
                fingerprintReady = Biometrics.isReady(context),
                lockedApps = repository.lockedApps.associateBy { it.packageName },
            )
        }
    }

    /** Loads the app list on first use (icons are slow to decode). */
    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.Default) { InstalledApps.load(getApplication()) }
        }
    }

    fun verifyCurrentPin(pin: String): Boolean = repository.verifyPin(pin)

    fun savePin(pin: String) {
        repository.savePin(pin)
        refresh()
    }

    fun setFingerprintEnabled(enabled: Boolean) {
        if (enabled && !Biometrics.isReady(getApplication())) return
        repository.fingerprintEnabled = enabled
        refresh()
    }

    fun setLockEnabled(enabled: Boolean) {
        if (enabled && (!_state.value.allPermissionsGranted || !repository.hasPin())) return
        repository.lockEnabled = enabled
        saved()
    }

    fun setAppLocked(packageName: String, locked: Boolean) {
        val others = repository.lockedApps.filterNot { it.packageName == packageName }
        repository.lockedApps = if (locked) others + LockedApp(packageName) else others
        saved()
    }

    fun setAppMode(packageName: String, mode: LockMode) = update(packageName) {
        if (it.mode == mode) it else LockedApp(packageName, mode, AppLockPolicy.defaultMinutes(mode))
    }

    fun setAppMinutes(packageName: String, minutes: Int) = update(packageName) { it.copy(minutes = minutes) }

    private fun update(packageName: String, change: (LockedApp) -> LockedApp) {
        repository.lockedApps = repository.lockedApps.map { if (it.packageName == packageName) change(it) else it }
        saved()
    }

    /** Settings changed: refresh the guard's in-memory copy and the screen. */
    private fun saved() {
        LockState.reload(getApplication())
        refresh()
    }
}
