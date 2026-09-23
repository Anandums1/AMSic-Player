package com.anandu.musicplayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun EqualizerAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        EqualizerBar(heightProvider = { bar1Height }, color = color)
        EqualizerBar(heightProvider = { bar2Height }, color = color)
        EqualizerBar(heightProvider = { bar3Height }, color = color)
    }
}

@Composable
private fun RowScope.EqualizerBar(
    heightProvider: () -> Float,
    color: Color
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleY = heightProvider().coerceIn(0.15f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

