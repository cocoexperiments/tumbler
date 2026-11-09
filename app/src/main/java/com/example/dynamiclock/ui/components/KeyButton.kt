package com.example.dynamiclock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared pressable key: springy press-scale and an animated red flash when [isError] is true.
 * Used by both the dynamic keypad buttons and the plain number pad.
 */
@Composable
fun KeyButton(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "keyScale",
    )
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = when {
            isError -> colors.errorContainer
            pressed -> colors.surfaceContainerHighest
            else -> colors.surfaceContainerHigh
        },
        animationSpec = tween(150),
        label = "keyContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isError) colors.onErrorContainer else colors.onSurface,
        animationSpec = tween(150),
        label = "keyContent",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = shape,
        color = container,
        contentColor = contentColor,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
