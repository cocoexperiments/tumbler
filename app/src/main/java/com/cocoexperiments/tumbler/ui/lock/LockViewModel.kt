package com.cocoexperiments.tumbler.ui.lock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewModelScope
import com.cocoexperiments.tumbler.data.PinRepository
import com.cocoexperiments.tumbler.domain.AppLockPolicy
import com.cocoexperiments.tumbler.domain.KeypadGenerator
import com.cocoexperiments.tumbler.domain.KeypadGroup
import com.cocoexperiments.tumbler.domain.LockMode
import com.cocoexperiments.tumbler.domain.LockedApp
import com.cocoexperiments.tumbler.domain.PinPolicy
import com.cocoexperiments.tumbler.domain.PinSession
import com.cocoexperiments.tumbler.domain.TapResult
import com.cocoexperiments.tumbler.domain.UnlockPolicy
import com.cocoexperiments.tumbler.service.LockState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(
    val groups: List<KeypadGroup> = emptyList(),
    /** Index of the PIN digit currently being matched (0 until pinLength). */
    val step: Int = 0,
    val pinLength: Int = PinPolicy.LENGTH,
    val isError: Boolean = false,
    /** Increments on every wrong tap; drives the shake animation. */
    val errorCount: Int = 0,
    val lockoutSeconds: Int = 0,
    /** Show the "Use fingerprint" button (set by the activity, which knows the sensor state). */
    val fingerprintAvailable: Boolean = false,
    /** The app being guarded; null for the in-app preview. */
    val appLabel: String? = null,
    val appIcon: ImageBitmap? = null,
    val mode: LockMode = LockMode.PRIVACY,
    /** Wellbeing: seconds left before the keypad works. */
    val pauseSecondsLeft: Int = 0,
    /** Wellbeing: how long the app stays open after unlocking, chosen from [grantOptions]. */
    val grantMinutes: Int = 0,
    val grantOptions: List<Int> = emptyList(),
    val opensToday: Int = 0,
) {
    val keypadEnabled: Boolean get() = lockoutSeconds == 0 && pauseSecondsLeft == 0
}

sealed interface LockEvent {
    /** Wrong group tapped: vibrate. */
    data object Rejected : LockEvent

    /** Whole PIN matched: dismiss the lock screen. */
    data object Unlocked : LockEvent
}

class LockViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PinRepository.get(application)
    private val generator = KeypadGenerator()
    private var session: PinSession? = null
    private var errorJob: Job? = null
    private var lockoutJob: Job? = null
    private var pauseJob: Job? = null
    private var app: LockedApp? = null

    private val _state = MutableStateFlow(LockUiState(groups = generator.generate()))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private val _events = Channel<LockEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Chooses which app this lock guards (null = preview) and starts a fresh attempt for it. */
    fun setTarget(packageName: String?) {
        app = packageName?.let { pkg ->
            LockState.appFor(pkg) ?: repository.lockedApps.firstOrNull { it.packageName == pkg }
        }
        val current = app
        val pm = getApplication<Application>().packageManager
        val label = current?.let { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() }.getOrNull() }
        val icon = current?.let { runCatching { pm.getApplicationIcon(it.packageName).toBitmap(160, 160).asImageBitmap() }.getOrNull() }
        val mode = current?.mode ?: LockMode.PRIVACY
        _state.update {
            it.copy(
                appLabel = label,
                appIcon = icon,
                mode = mode,
                grantMinutes = if (mode == LockMode.WELLBEING) current?.minutes ?: 0 else 0,
                grantOptions = if (mode == LockMode.WELLBEING) AppLockPolicy.WELLBEING_OPTIONS else emptyList(),
                opensToday = current?.let { a -> repository.opensToday(a.packageName) } ?: 0,
            )
        }
        newAttempt()
    }

    fun selectGrantMinutes(minutes: Int) {
        _state.update { it.copy(grantMinutes = minutes) }
    }

    fun setFingerprintAvailable(available: Boolean) {
        _state.update { it.copy(fingerprintAvailable = available) }
    }

    /** Starts a fresh attempt: step 0 and a freshly shuffled board. The board is also reshuffled after a wrong tap, but not after correct digits. */
    fun newAttempt() {
        val pin = repository.readPin()
        if (pin == null) {
            // No PIN registered: never trap the user behind a lock they cannot pass.
            _events.trySend(LockEvent.Unlocked)
            return
        }
        session = PinSession(pin)
        errorJob?.cancel()
        _state.update { it.copy(groups = generator.generate(), step = 0, isError = false) }
        startLockoutCountdown()
        startPause()
    }

    /** Wellbeing friction: the keypad stays disabled for a few seconds every time the lock appears. */
    private fun startPause() {
        pauseJob?.cancel()
        val seconds = AppLockPolicy.pauseSeconds(_state.value.mode)
        _state.update { it.copy(pauseSecondsLeft = seconds) }
        if (seconds == 0) return
        pauseJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                delay(1000)
                left--
                _state.update { it.copy(pauseSecondsLeft = left) }
            }
        }
    }

    /** Records the grant that lets the guarded app stay open, then tells the activity to finish. */
    private fun unlock() {
        app?.let { locked ->
            val minutes = if (locked.mode == LockMode.WELLBEING) _state.value.grantMinutes.takeIf { it > 0 } ?: locked.minutes else locked.minutes
            LockState.grant(locked.packageName, minutes)
            if (locked.mode == LockMode.WELLBEING) repository.recordOpen(locked.packageName)
        }
        _events.trySend(LockEvent.Unlocked)
    }

    /** Whether the fingerprint may be offered right now; [sensorReady] comes from the platform. */
    fun fingerprintAllowed(sensorReady: Boolean): Boolean =
        AppLockPolicy.fingerprintAllowedFor(_state.value.mode) &&
            UnlockPolicy.fingerprintAllowed(repository.fingerprintEnabled, repository.pinRequired, sensorReady)

    /** The system verified an enrolled fingerprint. Re-checks the policy so a stale prompt cannot unlock. */
    fun onFingerprintSucceeded(sensorReady: Boolean) {
        if (!fingerprintAllowed(sensorReady)) return
        repository.clearFailures()
        unlock()
    }

    fun onGroupTapped(index: Int) {
        val activeSession = session ?: return
        val current = _state.value
        if (current.isError || !current.keypadEnabled) return
        val group = current.groups.getOrNull(index) ?: return

        when (val result = activeSession.onGroupSelected(group)) {
            // Correct digit: the board stays put for the rest of this attempt.
            is TapResult.Advanced ->
                _state.update { it.copy(step = result.step) }

            TapResult.Completed -> {
                repository.clearFailures()
                repository.pinRequired = false
                _state.update { it.copy(step = it.pinLength) }
                unlock()
            }

            TapResult.Rejected -> onRejected()
        }
    }

    private fun onRejected() {
        repository.recordFailure()
        _events.trySend(LockEvent.Rejected)
        _state.update {
            it.copy(step = 0, groups = generator.generate(), isError = true, errorCount = it.errorCount + 1)
        }
        errorJob?.cancel()
        errorJob = viewModelScope.launch {
            delay(ERROR_FLASH_MILLIS)
            _state.update { it.copy(isError = false) }
        }
        startLockoutCountdown()
    }

    private fun startLockoutCountdown() {
        lockoutJob?.cancel()
        if (repository.lockoutRemainingMillis() <= 0L) {
            _state.update { it.copy(lockoutSeconds = 0) }
            return
        }
        lockoutJob = viewModelScope.launch {
            while (true) {
                val remaining = repository.lockoutRemainingMillis()
                _state.update { it.copy(lockoutSeconds = ((remaining + 999) / 1000).toInt()) }
                if (remaining <= 0L) break
                delay(250)
            }
        }
    }

    private companion object {
        const val ERROR_FLASH_MILLIS = 450L
    }
}
