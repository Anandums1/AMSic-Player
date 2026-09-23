package com.anandu.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

val AlphabetList = listOf('#') + ('A'..'Z').toList()

/**
 * Modern fast alphabet scrubber index bar for quick seeking through large track and artist lists.
 */
@Composable
fun AlphabetScrubber(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    availableLetters: Set<Char> = emptySet(),
) {
    var isDragging by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    var componentHeightPx by remember { mutableFloatStateOf(1f) }
    var touchYPx by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Auto-dismiss indicator bubble after drag stops
    LaunchedEffect(isDragging) {
        if (!isDragging && selectedLetter != null) {
            delay(600)
            selectedLetter = null
        }
    }

    val bubbleHalfSizePx = with(density) { 28.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Floating Letter Preview Bubble tracking finger position
        AnimatedVisibility(
            visible = selectedLetter != null,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(end = 44.dp)
                .offset {
                    val maxOffset = (componentHeightPx / 2f - bubbleHalfSizePx).coerceAtLeast(0f)
                    val bubbleYOffset = if (maxOffset > 0f) {
                        (touchYPx - componentHeightPx / 2f).coerceIn(-maxOffset, maxOffset)
                    } else {
                        0f
                    }
                    IntOffset(x = 0, y = bubbleYOffset.roundToInt())
                }
        ) {
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(8.dp, CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = selectedLetter?.toString() ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                }
            }
        }

        // Scrubber thumb column
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 12.dp)
                .onGloballyPositioned { componentHeightPx = it.size.height.toFloat() }
                .pointerInput(componentHeightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isDragging = true
                        touchYPx = down.position.y
                        val startLetter = getLetterAtOffset(down.position.y, componentHeightPx)
                        selectedLetter = startLetter
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetterSelected(startLetter)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            touchYPx = change.position.y
                            val letter = getLetterAtOffset(change.position.y, componentHeightPx)
                            if (letter != selectedLetter) {
                                selectedLetter = letter
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onLetterSelected(letter)
                            }
                        }
                        isDragging = false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AlphabetList.forEach { char ->
                val isSelected = selectedLetter == char
                val isAvailable = availableLetters.isEmpty() || availableLetters.contains(char)

                Text(
                    text = char.toString(),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
        }
    }
}

private fun getLetterAtOffset(y: Float, totalHeight: Float): Char {
    if (totalHeight <= 0f) return '#'
    val fraction = (y / totalHeight).coerceIn(0f, 0.999f)
    val index = (fraction * AlphabetList.size).toInt().coerceIn(0, AlphabetList.size - 1)
    return AlphabetList[index]
}
