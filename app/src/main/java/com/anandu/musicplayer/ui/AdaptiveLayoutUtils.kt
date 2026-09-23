package com.anandu.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Utility to calculate adaptive spacing based on screen aspect ratio.
 * Specifically tailored for 20:9 (tall) vs 16:9 (standard) displays.
 */
object AdaptiveLayout {
    
    @Composable
    fun isTallScreen(): Boolean {
        val size = LocalWindowInfo.current.containerSize
        val ratio = size.height.toFloat() / size.width.toFloat()
        return ratio >= 2.1f // 20:9 is ~2.22, 18:9 is 2.0
    }

    @Composable
    fun lerpSpacing(standard: Dp, tall: Dp): Dp {
        return if (isTallScreen()) tall else standard
    }
}
