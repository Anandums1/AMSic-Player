package com.anandu.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.anandu.musicplayer.ui.theme.AMSicPlayerTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModel()
    
    private var showPermissionRationale by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val audioGranted = grants[Manifest.permission.READ_MEDIA_AUDIO] == true
        if (audioGranted) {
            viewModel.loadTracks()
        } else {
            // Check if we should show rationale or if it's permanently denied
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO)) {
                showSettingsDialog = true
            } else {
                showPermissionRationale = true
            }
        }
    }

    private val metadataUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.retryPendingUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestPermissionsIfNeeded()
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.settingsManager.themeMode.collectAsState()
            val dynamicColor by viewModel.settingsManager.dynamicColor.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
            }

            AMSicPlayerTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor
            ) {
                val state by viewModel.playbackState.collectAsState()
                val progressState by viewModel.progressState.collectAsState()

                val view = LocalView.current
                SideEffect {
                    if (!view.isInEditMode) {
                        val window = (view.context as? Activity)?.window
                        if (window != null) {
                            val insetsController = WindowCompat.getInsetsController(window, view)
                            val shouldLightStatusBars = if (state.isNowPlayingOpen) false else !isDark
                            val shouldLightNavBars = if (state.isNowPlayingOpen) false else !isDark
                            insetsController.isAppearanceLightStatusBars = shouldLightStatusBars
                            insetsController.isAppearanceLightNavigationBars = shouldLightNavBars
                        }
                    }
                }

                val dominantColor: Color by animateColorAsState(
                    targetValue = Color(state.dominantColor),
                    animationSpec = tween(1000),
                    label = "dominantColor"
                )

                LaunchedEffect(state.pendingMetadataUpdate) {
                    state.pendingMetadataUpdate?.let { pendingIntent ->
                        try {
                            metadataUpdateLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        } catch (e: IntentSender.SendIntentException) {
                            e.printStackTrace()
                        } finally {
                            viewModel.consumePendingMetadataUpdate()
                        }
                    }
                }
                
                if (showPermissionRationale) {
                    PermissionRationaleDialog(
                        onDismiss = { showPermissionRationale = false },
                        onConfirm = {
                            showPermissionRationale = false
                            requestPermissions()
                        }
                    )
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        onDismiss = { showSettingsDialog = false },
                        onConfirm = {
                            showSettingsDialog = false
                            openSettings()
                        }
                    )
                }

                BackHandler(enabled = state.isNowPlayingOpen) {
                    viewModel.closeNowPlaying()
                }

                BackHandler(enabled = !state.isNowPlayingOpen && state.currentTab != PlayerViewModel.TAB_LIBRARY) {
                    viewModel.switchTab(PlayerViewModel.TAB_LIBRARY)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            Column {
                                AnimatedVisibility(
                                    visible = state.mediaId.isNotEmpty(),
                                    enter = expandVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        expandFrom = Alignment.Top
                                    ) + fadeIn(animationSpec = tween(200)),
                                    exit = shrinkVertically(
                                        animationSpec = tween(180, easing = FastOutLinearInEasing),
                                        shrinkTowards = Alignment.Top
                                    ) + fadeOut(animationSpec = tween(150))
                                ) {
                                    MiniPlayerBar(
                                        state = state,
                                        positionMs = progressState.positionMs,
                                        durationMs = progressState.durationMs,
                                        dominantColor = dominantColor,
                                        onTogglePlayPause = { viewModel.playPause() },
                                        onSkipNext = { viewModel.skipNext() },
                                        onSkipPrevious = { viewModel.skipPrevious() },
                                        onClick = { viewModel.openNowPlaying() }
                                    )
                                }
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                dominantColor.copy(alpha = 0.05f),
                                                dominantColor.copy(alpha = 0.15f)
                                            )
                                        )
                                    )
                                ) {
                                    NavigationBarItem(
                                        selected = state.currentTab == PlayerViewModel.TAB_LIBRARY,
                                        onClick = { viewModel.switchTab(PlayerViewModel.TAB_LIBRARY) },
                                        icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Library") },
                                        label = { Text("Library") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = dominantColor,
                                            selectedTextColor = dominantColor,
                                            indicatorColor = dominantColor.copy(alpha = 0.2f)
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = state.currentTab == PlayerViewModel.TAB_FAVORITES,
                                        onClick = { viewModel.switchTab(PlayerViewModel.TAB_FAVORITES) },
                                        icon = { Icon(Icons.Rounded.Favorite, contentDescription = "Favorites") },
                                        label = { Text("Favorites") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = dominantColor,
                                            selectedTextColor = dominantColor,
                                            indicatorColor = dominantColor.copy(alpha = 0.2f)
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = state.currentTab == PlayerViewModel.TAB_SETTINGS,
                                        onClick = { viewModel.switchTab(PlayerViewModel.TAB_SETTINGS) },
                                        icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                        label = { Text("Settings") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = dominantColor,
                                            selectedTextColor = dominantColor,
                                            indicatorColor = dominantColor.copy(alpha = 0.2f)
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                                    end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                                    bottom = innerPadding.calculateBottomPadding()
                                ),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            AnimatedContent(
                                targetState = state.currentTab,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> -width } + fadeOut()
                                        )
                                    } else {
                                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                            slideOutHorizontally { width -> width } + fadeOut()
                                        )
                                    }
                                },
                                label = "tabSwitchAnimation"
                            ) { targetTab ->
                                when (targetTab) {
                                    PlayerViewModel.TAB_LIBRARY -> LibraryScreen(viewModel = viewModel)
                                    PlayerViewModel.TAB_FAVORITES -> FavoritesScreen(viewModel = viewModel)
                                    PlayerViewModel.TAB_SETTINGS -> SettingsScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.switchTab(PlayerViewModel.TAB_LIBRARY) }
                                    )
                                }
                            }
                        }
                    }

                    // Full-screen Now Playing Overlay with smooth vertical animation
                    AnimatedVisibility(
                        visible = state.isNowPlayingOpen,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight },
                            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight },
                            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing)),
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    ) {
                        NowPlayingScreen(
                            viewModel = viewModel,
                            onCollapse = { viewModel.closeNowPlaying() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        
        // Always load tracks if we have audio permission, regardless of notifications
        if (audioPermission == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadTracks()
            
            // Still check for notifications to ensure the user gets controls
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions()
                }
            }
            return
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO)) {
            showPermissionRationale = true
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        permissionLauncher.launch(permissions)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_now_playing", false) == true) {
            viewModel.openNowPlaying()
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

@Composable
fun PermissionRationaleDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Permission Required") },
        text = { Text("A MSic player needs access to your audio files to build your music library. Please grant the storage permission.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Permanently Denied") },
        text = { Text("Storage permission was permanently denied. You can enable it manually in the app settings to use A MSic player.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MiniPlayerBar(
    state: PlayerViewModel.PlaybackState,
    positionMs: Long,
    durationMs: Long,
    dominantColor: Color,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClick: () -> Unit
) {
    val isTall = AdaptiveLayout.isTallScreen()
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTall) 74.dp else 68.dp)
            .graphicsLayer {
                translationX = animatedOffsetX.value
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragOffsetX = animatedOffsetX.value
                    },
                    onDragEnd = {
                        val threshold = 90f
                        if (dragOffsetX < -threshold) {
                            coroutineScope.launch {
                                animatedOffsetX.animateTo(-400f, tween(150, easing = FastOutLinearInEasing))
                                onSkipNext()
                                animatedOffsetX.snapTo(400f)
                                animatedOffsetX.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                            }
                        } else if (dragOffsetX > threshold) {
                            coroutineScope.launch {
                                animatedOffsetX.animateTo(400f, tween(150, easing = FastOutLinearInEasing))
                                onSkipPrevious()
                                animatedOffsetX.snapTo(-400f)
                                animatedOffsetX.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                            }
                        } else {
                            coroutineScope.launch {
                                animatedOffsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                            }
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animatedOffsetX.animateTo(0f)
                        }
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount
                        coroutineScope.launch {
                            animatedOffsetX.snapTo(dragOffsetX)
                        }
                    }
                )
            }
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column {
            // Subtle progress bar at the top of mini player
            LinearProgressIndicator(
                progress = { if (durationMs > 0) positionMs.toFloat() / durationMs else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = dominantColor,
                trackColor = dominantColor.copy(alpha = 0.1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = if (isTall) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(dominantColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    var isError by remember { mutableStateOf(false) }
                    if (state.albumArtUri != null) {
                        AsyncImage(
                            model = state.albumArtUri.toUri(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { isError = false },
                            onError = { isError = true }
                        )
                    }
                    if (state.albumArtUri == null || isError) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = dominantColor.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .background(dominantColor.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = dominantColor
                    )
                }
            }
        }
    }
}
