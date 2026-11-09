package com.cocoexperiments.tumbler.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Progress indicator: [filled] of [length] dots are lit; turns red on [isError]. */
@Composable
fun PinDots(length: Int, filled: Int, isError: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(length) { index ->
            val isFilled = index < filled
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.15f else 0.75f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dotScale",
            )
            val color by animateColorAsState(
                targetValue = when {
                    isError -> colors.error
                    isFilled -> colors.primary
                    else -> colors.outlineVariant
                },
                animationSpec = tween(200),
                label = "dotColor",
            )
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
