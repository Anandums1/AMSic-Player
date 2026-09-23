package com.anandu.musicplayer.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.anandu.musicplayer.R
import com.anandu.musicplayer.data.AudioFile
import com.anandu.musicplayer.ui.theme.AMSicPlayerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.playbackState.collectAsState()
    val progressState by viewModel.progressState.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    val vinylMode by viewModel.settingsManager.vinylMode.collectAsState()
    val hapticEnabled by viewModel.settingsManager.hapticFeedback.collectAsState()

    val currentAudioFile = remember(state.mediaId, viewModel.tracks.collectAsState().value) {
        viewModel.getCurrentAudioFile()
    }

    NowPlayingScreenContent(
        state = state,
        positionMs = progressState.positionMs,
        durationMs = progressState.durationMs,
        tracks = state.queue,
        currentAudioFile = currentAudioFile,
        playlists = libraryState.playlists,
        vinylMode = vinylMode,
        hapticEnabled = hapticEnabled,
        onRefresh = viewModel::loadTracks,
        onSetSleepTimer = viewModel::setSleepTimer,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
        onPlayTrack = viewModel::playTrack,
        onPlayQueueIndex = viewModel::seekToQueueIndex,
        onRemoveTrack = viewModel::removeTrack,
        onMoveTrack = viewModel::moveTrack,
        onToggleFavorite = viewModel::toggleFavorite,
        onSeek = viewModel::seekTo,
        onPlayPause = viewModel::playPause,
        onSkipNext = viewModel::skipNext,
        onSkipPrevious = viewModel::skipPrevious,
        onToggleRepeat = viewModel::toggleRepeatMode,
        onToggleShuffle = viewModel::toggleShuffleMode,
        onAddToPlaylist = { playlistName, track -> viewModel.addTrackToPlaylist(playlistName, track) },
        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
        onSaveCustomLyrics = { track, lrc -> viewModel.saveCustomLyrics(track, lrc) },
        onEditMetadata = { track, title, artist, album, genre, year, trackNum ->
            viewModel.updateTrackMetadata(track, title, artist, album, genre, year, trackNum)
        },
        onCollapse = onCollapse,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreenContent(
    state: PlayerViewModel.PlaybackState,
    positionMs: Long = state.positionMs,
    durationMs: Long = state.durationMs,
    tracks: List<AudioFile>,
    currentAudioFile: AudioFile?,
    playlists: List<Playlist>,
    vinylMode: Boolean,
    hapticEnabled: Boolean,
    onRefresh: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onPlayTrack: (AudioFile) -> Unit,
    onPlayQueueIndex: ((Int) -> Unit)? = null,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onToggleFavorite: (AudioFile) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddToPlaylist: (String, AudioFile) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onSaveCustomLyrics: (AudioFile, String) -> Unit,
    onEditMetadata: (AudioFile, String, String, String, String, Int, String) -> Unit,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSleepTimerMenu by remember { mutableStateOf(false) }
    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showFullscreenLyrics by remember { mutableStateOf(false) }
    var showAddEditLyricsDialog by remember { mutableStateOf(false) }
    var showTrackDetailsSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showEditMetadataDialog by remember { mutableStateOf(false) }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isDraggingDown by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 90.dp.toPx() }
    val screenHeightPx = with(density) { screenHeight.toPx() }

    val currentDragOffset = if (isDraggingDown) dragOffsetPx else animatedDragOffset.value

    val dominantColor by animateColorAsState(
        targetValue = Color(state.dominantColor),
        animationSpec = tween(1000),
        label = "dominantColor"
    )

    val rootBackground = remember(dominantColor) {
        val darkTop = Color(
            red = (dominantColor.red * 0.40f + 0.04f).coerceIn(0f, 1f),
            green = (dominantColor.green * 0.40f + 0.04f).coerceIn(0f, 1f),
            blue = (dominantColor.blue * 0.40f + 0.04f).coerceIn(0f, 1f),
            alpha = 1.0f
        )
        Brush.verticalGradient(
            colors = listOf(
                darkTop,
                Color(0xFF141218),
                Color(0xFF0F0E13),
                Color(0xFF08080C)
            )
        )
    }

    val triggerHaptic: () -> Unit = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val isExpanded by remember { derivedStateOf { scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded } }

    LaunchedEffect(scaffoldState.bottomSheetState) {
        snapshotFlow { scaffoldState.bottomSheetState.currentValue }
            .collect {
                triggerHaptic()
            }
    }

    val totalQueueDurationMs = remember(tracks) { tracks.sumOf { it.duration } }

    LaunchedEffect(state.isNowPlayingOpen) {
        if (state.isNowPlayingOpen) {
            animatedDragOffset.snapTo(0f)
            dragOffsetPx = 0f
            isDraggingDown = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(x = 0, y = currentDragOffset.roundToInt().coerceAtLeast(0)) }
            .pointerInput(isExpanded) {
                if (!isExpanded) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDraggingDown = true
                        },
                        onDragEnd = {
                            isDraggingDown = false
                            if (dragOffsetPx > dismissThresholdPx) {
                                coroutineScope.launch {
                                    animatedDragOffset.snapTo(dragOffsetPx)
                                    animatedDragOffset.animateTo(
                                        targetValue = screenHeightPx,
                                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                    )
                                    onCollapse()
                                    // Offset remains offscreen; reset upon reopening to avoid double-slide glitch
                                }
                            } else {
                                coroutineScope.launch {
                                    animatedDragOffset.snapTo(dragOffsetPx)
                                    animatedDragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    dragOffsetPx = 0f
                                }
                            }
                        },
                        onDragCancel = {
                            isDraggingDown = false
                            coroutineScope.launch {
                                animatedDragOffset.snapTo(dragOffsetPx)
                                animatedDragOffset.animateTo(0f)
                                dragOffsetPx = 0f
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0 || dragOffsetPx > 0) {
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                            }
                        }
                    )
                }
            }
            .background(rootBackground)
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 84.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            sheetShadowElevation = 16.dp,
        sheetDragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp, 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "UP NEXT • ${tracks.size} tracks (${formatQueueDuration(totalQueueDurationMs)})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        sheetContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight / 2)
            ) {
                QueueList(
                    tracks = tracks,
                    currentMediaId = state.mediaId,
                    onTrackClick = { index ->
                        triggerHaptic()
                        if (onPlayQueueIndex != null) {
                            onPlayQueueIndex(index)
                        } else if (index in tracks.indices) {
                            onPlayTrack(tracks[index])
                        }
                    },
                    onRemove = {
                        triggerHaptic()
                        onRemoveTrack(it)
                    },
                    onMove = { from, to ->
                        triggerHaptic()
                        onMoveTrack(from, to)
                    }
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ── Top Bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        triggerHaptic()
                        onCollapse()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    if (state.sleepTimerRemainingMs > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Timer: ${formatSleepTimer(state.sleepTimerRemainingMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        triggerHaptic()
                        onRefresh()
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            // 1. Track Details
                            DropdownMenuItem(
                                text = { Text("Track Details") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_info),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showTrackDetailsSheet = true
                                }
                            )

                            // 2. Add to Playlist
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_playlist_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showAddToPlaylistSheet = true
                                }
                            )

                            // 3. Edit Tags / Metadata
                            DropdownMenuItem(
                                text = { Text("Edit Metadata") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_edit),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showEditMetadataDialog = true
                                }
                            )

                            // 4. Edit / Add Lyrics
                            DropdownMenuItem(
                                text = { Text("Add / Edit Lyrics") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Lyrics,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showAddEditLyricsDialog = true
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // 5. Sleep Timer
                            DropdownMenuItem(
                                text = { Text("Sleep Timer") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showSleepTimerMenu = true
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = showSleepTimerMenu,
                            onDismissRequest = { showSleepTimerMenu = false }
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("$mins minutes") },
                                    onClick = {
                                        triggerHaptic()
                                        onSetSleepTimer(mins)
                                        showSleepTimerMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Custom...") },
                                onClick = {
                                    showCustomTimerDialog = true
                                    showSleepTimerMenu = false
                                }
                            )
                            if (state.sleepTimerRemainingMs > 0) {
                                DropdownMenuItem(
                                    text = { Text("Cancel Timer", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        triggerHaptic()
                                        onCancelSleepTimer()
                                        showSleepTimerMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (tracks.isEmpty()) {
                EmptyState { onRefresh() }
            } else {
                val albumArtScale by animateFloatAsState(
                    targetValue = if (isExpanded) 0.6f else 1f,
                    animationSpec = tween(300),
                    label = "albumArtScale"
                )
                val contentAlpha by animateFloatAsState(
                    targetValue = if (isExpanded) 0.5f else 1f,
                    animationSpec = tween(300),
                    label = "contentAlpha"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .graphicsLayer(alpha = contentAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val isTall = AdaptiveLayout.isTallScreen()

                    Spacer(Modifier.weight(if (isTall) 1.8f else 1.2f))
                    Spacer(Modifier.height(54.dp))

                    // ── Album Art (with Gestures & Vinyl Mode) ────────────────
                    var seekBadge by remember { mutableStateOf<String?>(null) }
                    var dragOffsetTotal by remember { mutableFloatStateOf(0f) }
                    val animatedAlbumSwipeOffset = remember { Animatable(0f) }
                    val currentPosState = rememberUpdatedState(positionMs)
                    val currentDurState = rememberUpdatedState(durationMs)

                    LaunchedEffect(seekBadge) {
                        if (seekBadge != null) {
                            delay(800)
                            seekBadge = null
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f * albumArtScale)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                translationX = animatedAlbumSwipeOffset.value
                                rotationZ = (animatedAlbumSwipeOffset.value / 45f).coerceIn(-7f, 7f)
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragOffsetTotal = animatedAlbumSwipeOffset.value
                                    },
                                    onDragEnd = {
                                        val threshold = 90f
                                        if (dragOffsetTotal < -threshold) {
                                            triggerHaptic()
                                            coroutineScope.launch {
                                                animatedAlbumSwipeOffset.animateTo(-500f, tween(160, easing = FastOutLinearInEasing))
                                                onSkipNext()
                                                animatedAlbumSwipeOffset.snapTo(400f)
                                                animatedAlbumSwipeOffset.animateTo(
                                                    0f,
                                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                                )
                                            }
                                        } else if (dragOffsetTotal > threshold) {
                                            triggerHaptic()
                                            coroutineScope.launch {
                                                animatedAlbumSwipeOffset.animateTo(500f, tween(160, easing = FastOutLinearInEasing))
                                                onSkipPrevious()
                                                animatedAlbumSwipeOffset.snapTo(-400f)
                                                animatedAlbumSwipeOffset.animateTo(
                                                    0f,
                                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                                )
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                animatedAlbumSwipeOffset.animateTo(
                                                    0f,
                                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                                                )
                                            }
                                        }
                                        dragOffsetTotal = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragOffsetTotal += dragAmount
                                        coroutineScope.launch {
                                            animatedAlbumSwipeOffset.snapTo(dragOffsetTotal)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        val width = size.width
                                        if (offset.x < width * 0.35f) {
                                            triggerHaptic()
                                            val newPos = (currentPosState.value - 10000L).coerceAtLeast(0L)
                                            onSeek(newPos)
                                            seekBadge = "-10s"
                                        } else if (offset.x > width * 0.65f) {
                                            triggerHaptic()
                                            val newPos = (currentPosState.value + 10000L).coerceAtMost(currentDurState.value)
                                            onSeek(newPos)
                                            seekBadge = "+10s"
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = state.albumArtUri to vinylMode,
                            animationSpec = tween(500),
                            label = "albumArtCrossfade"
                        ) { (artworkUri, isVinyl) ->
                            if (isVinyl) {
                                VinylRecord(
                                    artworkUri = artworkUri,
                                    isPlaying = state.isPlaying,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AlbumArt(
                                    artworkUri = artworkUri,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        // Quick-Seek Gesture Overlay Badge
                        if (seekBadge != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                shadowElevation = 8.dp
                            ) {
                                Text(
                                    text = seekBadge ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(if (isExpanded) 28.dp else 40.dp))

                    // ── Track Info ─────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            triggerHaptic()
                            showLyrics = !showLyrics
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = "Lyrics",
                                tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedContent(
                            targetState = state.title to state.artist,
                            transitionSpec = {
                                (slideInVertically { height -> height / 2 } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height / 2 } + fadeOut()
                                )
                            },
                            label = "trackInfoAnimation",
                            modifier = Modifier.weight(1f)
                        ) { (title, artist) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                if (state.bitrateBps > 0) {
                                    val bitrateKbps = state.bitrateBps / 1000
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text(
                                            text = "$bitrateKbps kbps",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val favScale by animateFloatAsState(
                            targetValue = if (state.isFavorite) 1.25f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "favScale"
                        )

                        IconButton(
                            onClick = {
                                triggerHaptic()
                                tracks.find { it.contentUri.toString() == state.mediaId }?.let {
                                    onToggleFavorite(it)
                                }
                            },
                            modifier = Modifier.graphicsLayer {
                                scaleX = favScale
                                scaleY = favScale
                            }
                        ) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (state.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(if (isExpanded) 0.dp else 4.dp))

                    if (showLyrics) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(12.dp)
                        ) {
                            LyricsView(
                                state = state,
                                positionMs = positionMs,
                                onSeek = {
                                    triggerHaptic()
                                    onSeek(it)
                                },
                                onExpandFullscreen = { showFullscreenLyrics = true },
                                onAddEditLyrics = { showAddEditLyricsDialog = true }
                            )
                        }
                    } else {
                        // ── Seek Bar ───────────────────────────────────────────
                        SeekBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeek = {
                                triggerHaptic()
                                onSeek(it)
                            },
                            dominantColor = dominantColor
                        )
                    }

                    Spacer(Modifier.height(if (isExpanded) 12.dp else 16.dp))

                    // ── Transport Controls ─────────────────────────────────
                    TransportControls(
                        isPlaying = state.isPlaying,
                        repeatMode = state.repeatMode,
                        shuffleModeEnabled = state.shuffleModeEnabled,
                        onPlayPause = {
                            triggerHaptic()
                            onPlayPause()
                        },
                        onSkipNext = {
                            triggerHaptic()
                            onSkipNext()
                        },
                        onSkipPrevious = {
                            triggerHaptic()
                            onSkipPrevious()
                        },
                        onToggleRepeat = {
                            triggerHaptic()
                            onToggleRepeat()
                        },
                        onToggleShuffle = {
                            triggerHaptic()
                            onToggleShuffle()
                        },
                        dominantColor = dominantColor
                    )

                    Spacer(Modifier.weight(if (isTall) 1.5f else 1f))
                    Spacer(Modifier.height(84.dp))
                }
            }
        }
    }

    // ── Fullscreen Lyrics Dialog ─────────────────────────────────────────
    if (showFullscreenLyrics) {
        FullscreenLyricsDialog(
            state = state,
            positionMs = positionMs,
            dominantColor = Color(state.dominantColor),
            onSeek = {
                triggerHaptic()
                onSeek(it)
            },
            onPlayPause = {
                triggerHaptic()
                onPlayPause()
            },
            onSkipNext = {
                triggerHaptic()
                onSkipNext()
            },
            onSkipPrevious = {
                triggerHaptic()
                onSkipPrevious()
            },
            onEditLyrics = { showAddEditLyricsDialog = true },
            onDismiss = { showFullscreenLyrics = false }
        )
    }

    // ── Add / Edit Lyrics Dialog ─────────────────────────────────────────
    if (showAddEditLyricsDialog) {
        val initialLrc = state.syncedLyrics?.joinToString("\n") { line ->
            val min = line.timestampMs / 60000
            val sec = (line.timestampMs % 60000) / 1000.0
            String.format(Locale.US, "[%02d:%05.2f]%s", min, sec, line.text)
        } ?: state.plainLyrics ?: ""

        val currentTrack = currentAudioFile ?: tracks.find { it.contentUri.toString() == state.mediaId }

        if (currentTrack != null) {
            AddEditLyricsDialog(
                initialLyrics = initialLrc,
                onDismiss = { showAddEditLyricsDialog = false },
                onSave = { lrc ->
                    triggerHaptic()
                    onSaveCustomLyrics(currentTrack, lrc)
                }
            )
        }
    }

    // ── Track Details Bottom Sheet ───────────────────────────────────────
    if (showTrackDetailsSheet) {
        val currentTrack = currentAudioFile ?: tracks.find { it.contentUri.toString() == state.mediaId }
        if (currentTrack != null) {
            TrackDetailsSheet(
                track = currentTrack,
                dominantColor = Color(state.dominantColor),
                onDismiss = { showTrackDetailsSheet = false },
                onEditMetadata = { showEditMetadataDialog = true }
            )
        }
    }

    // ── Add to Playlist Bottom Sheet ─────────────────────────────────────
    if (showAddToPlaylistSheet) {
        val currentTrack = currentAudioFile ?: tracks.find { it.contentUri.toString() == state.mediaId }
        if (currentTrack != null) {
            AddToPlaylistSheet(
                track = currentTrack,
                playlists = playlists,
                onDismiss = { showAddToPlaylistSheet = false },
                onAddToPlaylist = { playlistName ->
                    triggerHaptic()
                    onAddToPlaylist(playlistName, currentTrack)
                },
                onCreatePlaylist = { name ->
                    triggerHaptic()
                    onCreatePlaylist(name)
                }
            )
        }
    }

    // ── Edit Metadata Dialog ─────────────────────────────────────────────
    if (showEditMetadataDialog) {
        val currentTrack = currentAudioFile ?: tracks.find { it.contentUri.toString() == state.mediaId }
        if (currentTrack != null) {
            EditTrackMetadataDialog(
                track = currentTrack,
                onDismiss = { showEditMetadataDialog = false },
                onSave = { newTitle, newArtist, newAlbum, newGenre, newYear, newTrackNum ->
                    triggerHaptic()
                    onEditMetadata(currentTrack, newTitle, newArtist, newAlbum, newGenre, newYear, newTrackNum)
                }
            )
        }
    }

    // ── Custom Sleep Timer Dialog ────────────────────────────────────────
    if (showCustomTimerDialog) {
        var customMins by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomTimerDialog = false },
            title = { Text("Set Custom Timer") },
            text = {
                TextField(
                    value = customMins,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customMins = it },
                    placeholder = { Text("Minutes") },
                    suffix = { Text("min") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customMins.isNotBlank()) {
                            triggerHaptic()
                            onSetSleepTimer(customMins.toInt())
                            showCustomTimerDialog = false
                        }
                    }
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Interactive Synced Lyrics Component
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun LyricsView(
    state: PlayerViewModel.PlaybackState,
    positionMs: Long = state.positionMs,
    onSeek: (Long) -> Unit,
    onExpandFullscreen: () -> Unit,
    onAddEditLyrics: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLyricsLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else if (state.syncedLyrics != null) {
            val listState = rememberLazyListState()
            val currentLineIndex = state.syncedLyrics.indexOfLast { it.timestampMs <= positionMs }

            LaunchedEffect(currentLineIndex) {
                if (currentLineIndex >= 0) {
                    listState.animateScrollToItem(
                        (currentLineIndex - 1).coerceAtLeast(0)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(state.syncedLyrics, key = { _, line -> line.timestampMs }) { index, line ->
                    val isCurrentLine = index == currentLineIndex
                    Text(
                        text = line.text,
                        style = if (isCurrentLine) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                               else MaterialTheme.typography.bodyLarge,
                        color = if (isCurrentLine) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSeek(line.timestampMs) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        } else if (state.plainLyrics != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.plainLyrics,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "No lyrics found", color = Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAddEditLyrics) {
                        Text("+ Add Lyrics")
                    }
                }
            }
        }

        // Top-right action buttons (Fullscreen & Edit)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            IconButton(
                onClick = onAddEditLyrics,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = "Edit Lyrics",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onExpandFullscreen,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lyrics_fullscreen),
                    contentDescription = "Fullscreen Lyrics",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Fullscreen Immersive Karaoke View
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun FullscreenLyricsDialog(
    state: PlayerViewModel.PlaybackState,
    positionMs: Long,
    dominantColor: Color,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onEditLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            dominantColor.copy(alpha = 0.94f),
                            Color(0xFF0C0C12),
                            Color.Black
                        )
                    )
                )
                .systemBarsPadding()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.graphicsLayer { rotationZ = 180f }
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onEditLyrics) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = "Edit Lyrics",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Lyrics Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (state.syncedLyrics != null) {
                        val listState = rememberLazyListState()
                        val currentLineIndex = state.syncedLyrics.indexOfLast { it.timestampMs <= positionMs }

                        LaunchedEffect(currentLineIndex) {
                            if (currentLineIndex >= 0) {
                                listState.animateScrollToItem(
                                    (currentLineIndex - 2).coerceAtLeast(0)
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            contentPadding = PaddingValues(vertical = 40.dp)
                        ) {
                            itemsIndexed(state.syncedLyrics, key = { _, line -> line.timestampMs }) { index, line ->
                                val isCurrentLine = index == currentLineIndex
                                Text(
                                    text = line.text,
                                    style = if (isCurrentLine) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                           else MaterialTheme.typography.titleLarge,
                                    color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .clickable { onSeek(line.timestampMs) }
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else if (state.plainLyrics != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.plainLyrics,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "No lyrics found", color = Color.White.copy(alpha = 0.5f))
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = onEditLyrics) {
                                    Text("Add Lyrics")
                                }
                            }
                        }
                    }
                }

                // Mini bottom player controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSkipPrevious) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onPlayPause) {
                                Icon(
                                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        IconButton(onClick = onSkipNext) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Add / Edit Lyrics Dialog
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun AddEditLyricsDialog(
    initialLyrics: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialLyrics) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add / Edit Lyrics", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste plain text or synced LRC lyrics (e.g. [01:23.45] lyric text). The lyrics will be embedded and permanently preserved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("[00:15.00] First lyric line\n[00:20.50] Second lyric line") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text.trim())
                    }
                    onDismiss()
                }
            ) {
                Text("Save & Embed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Track Details & Audio Specs Sheet
// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackDetailsSheet(
    track: AudioFile,
    dominantColor: Color,
    onDismiss: () -> Unit,
    onEditMetadata: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Track Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                val isHiRes = track.bitrateBps >= 1000000 || track.sampleRateHz >= 96000
                val isLossless = track.mimeType.contains("flac", ignoreCase = true) || track.mimeType.contains("wav", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = dominantColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isHiRes) "HI-RES AUDIO" else if (isLossless) "LOSSLESS" else "STANDARD AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = dominantColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            DetailItem(label = "Title", value = track.title)
            DetailItem(label = "Artist", value = track.artist)
            DetailItem(label = "Album", value = track.album)
            DetailItem(label = "Format", value = track.mimeType.substringAfterLast('/').uppercase())
            if (track.bitrateBps > 0) {
                DetailItem(label = "Bitrate", value = "${track.bitrateBps / 1000} kbps")
            }
            if (track.sampleRateHz > 0) {
                DetailItem(label = "Sample Rate", value = "${track.sampleRateHz / 1000.0} kHz")
            }
            if (track.sizeBytes > 0) {
                DetailItem(label = "File Size", value = String.format(Locale.US, "%.1f MB", track.sizeBytes / (1024.0 * 1024.0)))
            }
            if (track.genre.isNotBlank()) {
                DetailItem(label = "Genre", value = track.genre)
            }
            if (track.year > 0) {
                DetailItem(label = "Year", value = track.year.toString())
            }
            if (track.filePath.isNotBlank()) {
                DetailItem(label = "Path", value = track.filePath)
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onDismiss()
                    onEditMetadata()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = dominantColor)
            ) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit Tags & Metadata")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Add to Playlist Sheet
// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    track: AudioFile,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { showCreateDialog = true }) {
                    Text("+ New Playlist")
                }
            }

            Spacer(Modifier.height(8.dp))

            val userPlaylists = playlists.filter { it.name != "Device Files" }
            if (userPlaylists.isEmpty()) {
                Text(
                    text = "No custom playlists yet. Create one!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(userPlaylists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${playlist.tracks.size} tracks") },
                            leadingContent = {
                                Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                onAddToPlaylist(playlist.name)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim())
                            onAddToPlaylist(newName.trim())
                            showCreateDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("Create & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Edit Track Metadata Dialog
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun EditTrackMetadataDialog(
    track: AudioFile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, String) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var genre by remember { mutableStateOf(track.genre) }
    var yearText by remember { mutableStateOf(if (track.year > 0) track.year.toString() else "") }
    var trackNum by remember { mutableStateOf(track.trackNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Metadata", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = yearText, onValueChange = { if (it.all { c -> c.isDigit() }) yearText = it }, label = { Text("Year") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = trackNum, onValueChange = { trackNum = it }, label = { Text("Track Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val y = yearText.toIntOrNull() ?: 0
                    onSave(title.trim(), artist.trim(), album.trim(), genre.trim(), y, trackNum.trim())
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Vinyl Record Turntable Animation Component
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun VinylRecord(
    artworkUri: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
                )
            }
        } else {
            rotation.animateTo(
                targetValue = rotation.value + 35f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 28.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.8f)
            )
            .clip(CircleShape)
            .background(Color(0xFF111115))
            .graphicsLayer { rotationZ = rotation.value }
            .drawBehind {
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                for (r in 36..radius.toInt() step 12) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = r.toFloat(),
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = radius * 0.86f,
                    center = center,
                    style = Stroke(width = 6f)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.44f)
                .clip(CircleShape)
                .background(Color(0xFF22222E)),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri.toUri(),
                    contentDescription = "Vinyl Label",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0E))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Up Next Queue List
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun QueueList(
    tracks: List<AudioFile>,
    currentMediaId: String,
    onTrackClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()
    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var autoscrollJob by remember { mutableStateOf<Job?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val isPlaying = track.contentUri.toString() == currentMediaId
            val isDragged = index == draggedItemIndex
            val currentIndex by rememberUpdatedState(index)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .graphicsLayer {
                        if (isDragged) {
                            translationY = dragOffset
                            shadowElevation = 8.dp.toPx()
                            scaleX = 1.02f
                            scaleY = 1.02f
                        }
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDragged) MaterialTheme.colorScheme.surfaceVariant
                        else if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .clickable { onTrackClick(currentIndex) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Reorder",
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedItemIndex = currentIndex
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y

                                    val layoutInfo = listState.layoutInfo
                                    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                    val absoluteDragY = change.position.y + (layoutInfo.visibleItemsInfo.find { it.index == currentIndex }?.offset ?: 0) + dragOffset

                                    if (absoluteDragY < 50f && listState.firstVisibleItemIndex > 0) {
                                        if (autoscrollJob == null) {
                                            autoscrollJob = scope.launch {
                                                while (isActive) {
                                                    listState.scrollBy(-16f)
                                                    delay(16)
                                                }
                                            }
                                        }
                                    } else if (absoluteDragY > viewportHeight - 50f) {
                                        if (autoscrollJob == null) {
                                            autoscrollJob = scope.launch {
                                                while (isActive) {
                                                    listState.scrollBy(16f)
                                                    delay(16)
                                                }
                                            }
                                        }
                                    } else {
                                        autoscrollJob?.cancel()
                                        autoscrollJob = null
                                    }

                                    val itemHeight = 64.dp.toPx()
                                    val swapThreshold = itemHeight * 0.7f
                                    if (dragOffset > swapThreshold && draggedItemIndex < tracks.size - 1) {
                                        onMove(draggedItemIndex, draggedItemIndex + 1)
                                        draggedItemIndex += 1
                                        dragOffset -= itemHeight
                                    } else if (dragOffset < -swapThreshold && draggedItemIndex > 0) {
                                        onMove(draggedItemIndex, draggedItemIndex - 1)
                                        draggedItemIndex -= 1
                                        dragOffset += itemHeight
                                    }
                                },
                                onDragEnd = {
                                    draggedItemIndex = -1
                                    dragOffset = 0f
                                    autoscrollJob?.cancel()
                                    autoscrollJob = null
                                },
                                onDragCancel = {
                                    draggedItemIndex = -1
                                    dragOffset = 0f
                                    autoscrollJob?.cancel()
                                    autoscrollJob = null
                                }
                            )
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    var isError by remember { mutableStateOf(false) }
                    if (track.albumArtUri != null) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { isError = false },
                            onError = { isError = true }
                        )
                    }
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EqualizerAnimation(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (track.albumArtUri == null || isError) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onRemove(currentIndex) }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No music found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Grant storage permission and add some songs to your device, then refresh.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Standard Album Art
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun AlbumArt(artworkUri: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        var isError by remember { mutableStateOf(false) }

        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri.toUri(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { isError = true }
            )
        }

        if (artworkUri == null || isError) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Seek Bar with Elapsed vs Remaining Time Toggle
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    dominantColor: Color,
    modifier: Modifier = Modifier,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var latchedSeekPositionMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(positionMs) {
        if (latchedSeekPositionMs != null) {
            if (kotlin.math.abs(positionMs - (latchedSeekPositionMs ?: 0L)) < 1500L) {
                latchedSeekPositionMs = null
            }
        }
    }

    LaunchedEffect(latchedSeekPositionMs) {
        if (latchedSeekPositionMs != null) {
            delay(1200)
            latchedSeekPositionMs = null
        }
    }

    val effectivePositionMs = if (isSeeking) {
        (seekValue * durationMs).toLong()
    } else {
        latchedSeekPositionMs ?: positionMs
    }

    val fraction = if (durationMs > 0) (effectivePositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayFraction = if (isSeeking) seekValue else fraction

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = displayFraction,
            onValueChange = {
                isSeeking = true
                seekValue = it
            },
            onValueChangeFinished = {
                if (durationMs > 0) {
                    val targetMs = (seekValue * durationMs).toLong()
                    latchedSeekPositionMs = targetMs
                    onSeek(targetMs)
                }
                isSeeking = false
            },
            colors = SliderDefaults.colors(
                thumbColor = dominantColor,
                activeTrackColor = dominantColor,
                inactiveTrackColor = dominantColor.copy(alpha = 0.2f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(effectivePositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val remainingMs = (durationMs - effectivePositionMs).coerceAtLeast(0L)
            Text(
                text = if (showRemainingTime) "-${formatTime(remainingMs)}" else formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showRemainingTime = !showRemainingTime }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Transport Controls
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    dominantColor: Color,
    modifier: Modifier = Modifier,
) {
    val playPauseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.92f,
        animationSpec = tween(durationMillis = 150),
        label = "playPauseScale",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleModeEnabled) dominantColor
                       else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer(scaleX = playPauseScale, scaleY = playPauseScale)
                .shadow(12.dp, CircleShape, ambientColor = dominantColor.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            dominantColor,
                            dominantColor.copy(alpha = 0.8f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White,
                )
            }
        }

        IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        IconButton(onClick = onToggleRepeat) {
            val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                       else Icons.Rounded.Repeat
            val tint = if (repeatMode != Player.REPEAT_MODE_OFF) dominantColor
                       else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            Icon(
                imageVector = icon,
                contentDescription = "Repeat",
                tint = tint
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatQueueDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatSleepTimer(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return when {
        totalSeconds > 60 -> {
            val minutes = totalSeconds / 60
            "$minutes min left"
        }
        totalSeconds >= 10 -> {
            val roundedTenSec = (totalSeconds / 10) * 10
            "$roundedTenSec sec left"
        }
        else -> {
            "$totalSeconds sec left"
        }
    }
}
