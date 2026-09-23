package com.anandu.musicplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val excludeShortClips by viewModel.settingsManager.excludeShortClips.collectAsState()
    val vinylMode by viewModel.settingsManager.vinylMode.collectAsState()
    val hapticFeedback by viewModel.settingsManager.hapticFeedback.collectAsState()
    val themeMode by viewModel.settingsManager.themeMode.collectAsState()
    val dynamicColor by viewModel.settingsManager.dynamicColor.collectAsState()
    val autoEmbedLyrics by viewModel.settingsManager.autoEmbedLyrics.collectAsState()
    val pauseOnDisconnect by viewModel.settingsManager.pauseOnDisconnect.collectAsState()
    val swipeGesturesEnabled by viewModel.settingsManager.swipeGesturesEnabled.collectAsState()

    val libState by viewModel.libraryState.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var isRescanning by remember { mutableStateOf(false) }
    var rescanRotation by remember { mutableFloatStateOf(0f) }

    val rotationAngle by animateFloatAsState(
        targetValue = rescanRotation,
        animationSpec = tween(700),
        label = "rescanRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Library Overview Banner ─────────────────────────────────────────
            LibraryOverviewCard(
                songCount = libState.allSongs.size,
                totalDurationMs = libState.allSongs.sumOf { it.duration },
                artistCount = libState.artists.size,
                albumCount = libState.albums.size
            )

            // ── Appearance & Theme ──────────────────────────────────────────────
            SettingsCategoryCard(
                title = "Appearance & Theme",
                icon = Icons.Rounded.Palette
            ) {
                val themeLabel = when (themeMode) {
                    "light" -> "Light Theme"
                    "dark" -> "Dark Theme"
                    else -> "System Default"
                }

                SettingsClickableItem(
                    headline = "App Theme",
                    supporting = themeLabel,
                    icon = when (themeMode) {
                        "light" -> Icons.Rounded.LightMode
                        "dark" -> Icons.Rounded.DarkMode
                        else -> Icons.Rounded.BrightnessAuto
                    },
                    onClick = { showThemeDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    headline = "Material You Dynamic Colors",
                    supporting = "Use wallpaper-derived dynamic palette on Android 12+",
                    icon = Icons.Rounded.ColorLens,
                    checked = dynamicColor,
                    onCheckedChange = { viewModel.settingsManager.setDynamicColor(it) }
                )
            }

            // ── Now Playing & Display ───────────────────────────────────────────
            SettingsCategoryCard(
                title = "Now Playing & Display",
                icon = Icons.Rounded.MusicNote
            ) {
                SettingsSwitchItem(
                    headline = "Vinyl Turntable Animation",
                    supporting = "Display realistic rotating vinyl record for album art in Now Playing",
                    icon = Icons.Rounded.Album,
                    checked = vinylMode,
                    onCheckedChange = { viewModel.settingsManager.setVinylMode(it) }
                )
            }

            // ── Audio & Playback ────────────────────────────────────────────────
            SettingsCategoryCard(
                title = "Audio & Playback",
                icon = Icons.Rounded.Headphones
            ) {
                SettingsSwitchItem(
                    headline = "Auto-embed Lyrics",
                    supporting = "Save fetched and edited lyrics to local storage for offline use",
                    icon = Icons.Rounded.Lyrics,
                    checked = autoEmbedLyrics,
                    onCheckedChange = { viewModel.settingsManager.setAutoEmbedLyrics(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    headline = "Pause on Disconnect",
                    supporting = "Pause playback automatically when headphones or Bluetooth disconnect",
                    icon = Icons.Rounded.PauseCircle,
                    checked = pauseOnDisconnect,
                    onCheckedChange = { viewModel.settingsManager.setPauseOnDisconnect(it) }
                )
            }

            // ── Library & Scanning ──────────────────────────────────────────────
            SettingsCategoryCard(
                title = "Library & Scanning",
                icon = Icons.Rounded.FolderOpen
            ) {
                SettingsSwitchItem(
                    headline = "Exclude Short Audio Clips",
                    supporting = "Filter out audio files shorter than 30 seconds (voice notes, alerts)",
                    icon = Icons.Rounded.Timer,
                    checked = excludeShortClips,
                    onCheckedChange = { viewModel.settingsManager.setExcludeShortClips(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickableItem(
                    headline = "Rescan Media Library",
                    supporting = if (isRescanning) "Scanning storage..." else "Refresh songs, artists, and albums from device",
                    icon = Icons.Rounded.Refresh,
                    iconModifier = Modifier.rotate(rotationAngle),
                    trailingContent = {
                        if (isRescanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    },
                    onClick = {
                        if (!isRescanning) {
                            isRescanning = true
                            rescanRotation += 360f
                            viewModel.rescanLibrary()
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(800)
                                isRescanning = false
                                Toast.makeText(context, "Library refreshed (${libState.allSongs.size} songs found)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // ── Gestures & Haptics ──────────────────────────────────────────────
            SettingsCategoryCard(
                title = "Gestures & Haptics",
                icon = Icons.Rounded.TouchApp
            ) {
                SettingsSwitchItem(
                    headline = "Haptic Feedback",
                    supporting = "Vibrate on button taps, gestures, and alphabet index scrubbing",
                    icon = Icons.Rounded.Vibration,
                    checked = hapticFeedback,
                    onCheckedChange = { viewModel.settingsManager.setHapticFeedback(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsSwitchItem(
                    headline = "Library Swipe Gestures",
                    supporting = "Swipe songs right to add to queue, swipe left to toggle favorite",
                    icon = Icons.Rounded.Swipe,
                    checked = swipeGesturesEnabled,
                    onCheckedChange = { viewModel.settingsManager.setSwipeGesturesEnabled(it) }
                )
            }

            // ── About & System ──────────────────────────────────────────────────
            SettingsCategoryCard(
                title = "About & System",
                icon = Icons.Rounded.Info
            ) {
                SettingsClickableItem(
                    headline = "Set as Default Music Player",
                    supporting = "Configure Android app defaults for opening audio files (.mp3, .flac)",
                    icon = Icons.Rounded.SettingsApplications,
                    onClick = { openAppInfoSettings(context) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = { Text("AMSic Player", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Version 1.0.0 (Release) • Built with Jetpack Compose & Media3") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "v1.0.0",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsClickableItem(
                    headline = "Open Source Licenses",
                    supporting = "View credits and third-party software libraries",
                    icon = Icons.Rounded.Code,
                    onClick = { showCreditsDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Theme Selection Dialog ──────────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose App Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val themes = listOf(
                        Triple("system", "System Default", Icons.Rounded.BrightnessAuto),
                        Triple("dark", "Dark Theme", Icons.Rounded.DarkMode),
                        Triple("light", "Light Theme", Icons.Rounded.LightMode)
                    )

                    themes.forEach { (mode, label, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.settingsManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    viewModel.settingsManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Open Source Credits Dialog ──────────────────────────────────────────
    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Source Credits", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "AMSic Player is built with modern open-source Android libraries and tools:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val libraries = listOf(
                        "AndroidX Media3 (ExoPlayer)" to "High performance audio playback, audio focus, and media session",
                        "Jetpack Compose & Material 3" to "Declarative, reactive UI and expressive design system",
                        "Android Room" to "Fast, reliable SQLite database for playlists and listening analytics",
                        "Coil 3" to "Asynchronous album art loading and palette extraction",
                        "Koin" to "Lightweight dependency injection framework for Kotlin",
                        "AndroidX Glance" to "Interactive homescreen widgets"
                    )

                    libraries.forEach { (name, desc) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// ── Reusable Component Cards ──────────────────────────────────────────

@Composable
private fun LibraryOverviewCard(
    songCount: Int,
    totalDurationMs: Long,
    artistCount: Int,
    albumCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Library Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(icon = Icons.Rounded.MusicNote, label = "Songs", value = "$songCount")
                StatChip(icon = Icons.Rounded.Schedule, label = "Time", value = formatDurationShort(totalDurationMs))
                StatChip(icon = Icons.Rounded.Person, label = "Artists", value = "$artistCount")
                StatChip(icon = Icons.Rounded.Album, label = "Albums", value = "$albumCount")
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(headline, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun SettingsClickableItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    iconModifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(headline, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = iconModifier
            )
        },
        trailingContent = trailingContent ?: {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatDurationShort(totalDurationMs: Long): String {
    val totalSeconds = totalDurationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
