package com.cocoexperiments.tumbler.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.ui.components.KeypadGrid
import com.cocoexperiments.tumbler.ui.components.PinDots
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

@Composable
fun LockScreen(
    state: LockUiState,
    onGroupTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shake = remember { Animatable(0f) }
    val density = LocalDensity.current.density

    // Horizontal shake on every wrong tap.
    LaunchedEffect(state.errorCount) {
        if (state.errorCount > 0) {
            for (target in listOf(-18f, 18f, -12f, 12f, -6f, 6f, 0f)) {
                shake.animateTo(target, tween(45))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.surfaceContainer, colors.background)))
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        ClockHeader()
        Spacer(Modifier.weight(1f))

        Text(
            text = stringResource(R.string.lock_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_step, (state.step + 1).coerceAtMost(state.pinLength), state.pinLength),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
        Spacer(Modifier.height(20.dp))
        PinDots(length = state.pinLength, filled = state.step, isError = state.isError)

        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible = state.lockoutSeconds > 0) {
                Text(
                    text = stringResource(R.string.lock_lockout, state.lockoutSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }

        KeypadGrid(
            groups = state.groups,
            isError = state.isError,
            enabled = state.lockoutSeconds == 0,
            onGroupTapped = onGroupTapped,
            modifier = Modifier.graphicsLayer { translationX = shake.value * density },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ClockHeader() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - now.second * 1000L - now.nano / 1_000_000L)
        }
    }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM") }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = now.format(timeFormat),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
        )
        Text(
            text = now.format(dateFormat),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
