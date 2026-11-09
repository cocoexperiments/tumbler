package com.cocoexperiments.tumbler.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cocoexperiments.tumbler.domain.KeypadGenerator
import com.cocoexperiments.tumbler.domain.KeypadGroup

/** 3x3 grid of digit-group buttons. Digits cross-fade and scale in whenever the board is reshuffled. */
@Composable
fun KeypadGrid(
    groups: List<KeypadGroup>,
    isError: Boolean,
    enabled: Boolean,
    onGroupTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 3
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0 until KeypadGenerator.GROUP_COUNT / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    val group = groups.getOrNull(index)
                    KeyButton(
                        onClick = { onGroupTapped(index) },
                        shape = RoundedCornerShape(28.dp),
                        enabled = enabled && group != null,
                        isError = isError,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.1f),
                    ) {
                        AnimatedContent(
                            targetState = group,
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.85f))
                                    .togetherWith(fadeOut(tween(90)))
                            },
                            label = "groupDigits",
                        ) { shown ->
                            GroupDigits(shown)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDigits(group: KeypadGroup?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        group?.digits?.forEach { digit ->
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
