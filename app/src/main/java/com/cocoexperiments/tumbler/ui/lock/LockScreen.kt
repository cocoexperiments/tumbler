package com.cocoexperiments.tumbler.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cocoexperiments.tumbler.R
import com.cocoexperiments.tumbler.ui.components.KeypadGrid
import com.cocoexperiments.tumbler.ui.components.PinDots
import com.cocoexperiments.tumbler.ui.components.Wordmark

@Composable
fun LockScreen(
    state: LockUiState,
    onGroupTapped: (Int) -> Unit,
    onUseFingerprint: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
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
        Spacer(Modifier.height(40.dp))
        AppHeader(state)
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

        val lockoutAlpha by animateFloatAsState(
            targetValue = if (state.lockoutSeconds > 0) 1f else 0f,
            label = "lockoutAlpha",
        )
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.lock_lockout, state.lockoutSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                modifier = Modifier.graphicsLayer { alpha = lockoutAlpha },
            )
        }

        KeypadGrid(
            groups = state.groups,
            isError = state.isError,
            enabled = state.keypadEnabled,
            onGroupTapped = onGroupTapped,
            modifier = Modifier.graphicsLayer {
                translationX = shake.value * density
                alpha = if (state.pauseSecondsLeft > 0) 0.3f else 1f
            },
        )
        // Fixed height so the keypad does not move when the button or chips appear.
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            when {
                state.grantOptions.isNotEmpty() -> GrantChips(state, onSelectMinutes)
                state.fingerprintAvailable ->
                    TextButton(onClick = onUseFingerprint) { Text(stringResource(R.string.fingerprint_button)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Wordmark(
            modifier = Modifier.graphicsLayer { alpha = 0.7f },
            horizontalAlignment = Alignment.CenterHorizontally,
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Which app is locked, or the brand name for the in-app preview. Wellbeing adds the countdown. */
@Composable
private fun AppHeader(state: LockUiState) {
    val colors = MaterialTheme.colorScheme
    val label = state.appLabel
    val pausing = state.pauseSecondsLeft > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.appIcon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = when {
                pausing -> stringResource(R.string.lock_pause_title)
                label != null -> stringResource(R.string.lock_title, label)
                else -> stringResource(R.string.lock_title_preview)
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when {
                pausing && label != null && state.opensToday > 0 ->
                    pluralStringResource(R.plurals.lock_pause_opens, state.opensToday, label, state.opensToday)
                pausing && label != null -> stringResource(R.string.lock_pause_plain, label)
                state.grantOptions.isNotEmpty() -> stringResource(R.string.lock_open_for)
                else -> stringResource(R.string.lock_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (pausing) {
            Text(
                text = stringResource(R.string.lock_pause_countdown, state.pauseSecondsLeft),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun GrantChips(state: LockUiState, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        state.grantOptions.forEach { minutes ->
            FilterChip(
                selected = minutes == state.grantMinutes,
                onClick = { onSelect(minutes) },
                label = { Text(stringResource(R.string.minutes_short, minutes)) },
            )
        }
    }
}
