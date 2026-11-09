package com.example.dynamiclock.ui.lock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dynamiclock.data.PinRepository
import com.example.dynamiclock.domain.KeypadGenerator
import com.example.dynamiclock.domain.KeypadGroup
import com.example.dynamiclock.domain.PinPolicy
import com.example.dynamiclock.domain.PinSession
import com.example.dynamiclock.domain.TapResult
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
)

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

    private val _state = MutableStateFlow(LockUiState(groups = generator.generate()))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    private val _events = Channel<LockEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        newAttempt()
    }

    /** Starts a fresh attempt: step 0 and a freshly shuffled board. Called on every lock-screen show. */
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
    }

    fun onGroupTapped(index: Int) {
        val activeSession = session ?: return
        val current = _state.value
        if (current.isError || current.lockoutSeconds > 0) return
        val group = current.groups.getOrNull(index) ?: return

        when (val result = activeSession.onGroupSelected(group)) {
            is TapResult.Advanced ->
                _state.update { it.copy(step = result.step, groups = generator.generate()) }

            TapResult.Completed -> {
                repository.clearFailures()
                _state.update { it.copy(step = it.pinLength) }
                _events.trySend(LockEvent.Unlocked)
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
