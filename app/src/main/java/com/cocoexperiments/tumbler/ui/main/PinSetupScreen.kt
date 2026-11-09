package com.cocoexperiments.tumbler.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.domain.PinPolicy
import com.cocoexperiments.tumbler.ui.components.NumberPad
import com.cocoexperiments.tumbler.ui.components.PinDots
import com.cocoexperiments.tumbler.ui.components.Wordmark
import com.cocoexperiments.tumbler.util.Haptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SetupPhase { VerifyCurrent, EnterNew, ConfirmNew }

/**
 * Registration flow: enter a 4-digit PIN, then confirm it.
 * When [requireCurrentPin] is true (changing an existing PIN) the current PIN is verified first.
 */
@Composable
fun PinSetupScreen(
    requireCurrentPin: Boolean,
    verifyCurrentPin: (String) -> Boolean,
    onPinSet: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Deliberately not rememberSaveable: the PIN must never be written into saved instance state.
    var phase by remember { mutableStateOf(if (requireCurrentPin) SetupPhase.VerifyCurrent else SetupPhase.EnterNew) }
    var input by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorCount by remember { mutableIntStateOf(0) }

    fun fail(message: Int, nextPhase: SetupPhase) {
        scope.launch {
            isError = true
            errorMessage = message
            errorCount++
            Haptics.error(context)
            delay(500)
            input = ""
            firstEntry = ""
            phase = nextPhase
            isError = false
            busy = false
        }
    }

    fun evaluate() {
        when (phase) {
            SetupPhase.VerifyCurrent ->
                if (verifyCurrentPin(input)) {
                    input = ""
                    errorMessage = null
                    phase = SetupPhase.EnterNew
                    busy = false
                } else {
                    fail(R.string.setup_error_wrong, SetupPhase.VerifyCurrent)
                }

            SetupPhase.EnterNew -> {
                firstEntry = input
                input = ""
                errorMessage = null
                phase = SetupPhase.ConfirmNew
                busy = false
            }

            SetupPhase.ConfirmNew ->
                if (input == firstEntry) onPinSet(input) else fail(R.string.setup_error_mismatch, SetupPhase.EnterNew)
        }
    }

    fun onDigit(digit: Int) {
        if (busy || input.length >= PinPolicy.LENGTH) return
        input += digit
        if (input.length == PinPolicy.LENGTH) {
            busy = true
            scope.launch {
                delay(150) // let the last dot animate in before moving on
                evaluate()
            }
        }
    }

    val title = when (phase) {
        SetupPhase.VerifyCurrent -> R.string.setup_title_verify
        SetupPhase.EnterNew -> R.string.setup_title_new
        SetupPhase.ConfirmNew -> R.string.setup_title_confirm
    }
    val subtitle = when (phase) {
        SetupPhase.VerifyCurrent -> R.string.setup_subtitle_verify
        SetupPhase.EnterNew -> R.string.setup_subtitle_new
        SetupPhase.ConfirmNew -> R.string.setup_subtitle_confirm
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Wordmark(horizontalAlignment = Alignment.CenterHorizontally)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMessage?.let { stringResource(it) } ?: stringResource(subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PinDots(length = PinPolicy.LENGTH, filled = input.length, isError = isError)
        Spacer(Modifier.height(40.dp))
        NumberPad(
            onDigit = ::onDigit,
            onBackspace = { if (!busy) input = input.dropLast(1) },
            enabled = !busy,
        )
        Spacer(Modifier.height(16.dp))
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}
