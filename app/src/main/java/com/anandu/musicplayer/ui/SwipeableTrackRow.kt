package com.anandu.musicplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Wraps a track item with smooth swipe-to-action gestures:
 * - Swipe right: Add to Queue
 * - Swipe left: Toggle Favorite
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTrackRow(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val haptic = LocalHapticFeedback.current
    val currentSwipeRight by rememberUpdatedState(onSwipeRight)
    val currentSwipeLeft by rememberUpdatedState(onSwipeLeft)
    var pendingAction by remember { mutableStateOf<SwipeToDismissBoxValue?>(null) }

    LaunchedEffect(pendingAction) {
        pendingAction?.let { action ->
            when (action) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentSwipeRight()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentSwipeLeft()
                }
                else -> {}
            }
            pendingAction = null
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                pendingAction = value
            }
            false // Snap back
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> Color.Transparent
                },
                label = "swipeBgColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Queue,
                            contentDescription = "Add to Queue",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Add to Queue",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Favorite",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Favorite",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        },
        content = {
            content()
        }
    )
}
