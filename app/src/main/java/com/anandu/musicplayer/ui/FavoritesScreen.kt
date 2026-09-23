package com.anandu.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anandu.musicplayer.data.AudioFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val libState by viewModel.libraryState.collectAsState()
    val playState by viewModel.playbackState.collectAsState()
    val hapticEnabled by viewModel.settingsManager.hapticFeedback.collectAsState()
    val swipeGesturesEnabled by viewModel.settingsManager.swipeGesturesEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showTrackInfo by remember { mutableStateOf<AudioFile?>(null) }
    var showMetadataEditor by remember { mutableStateOf<AudioFile?>(null) }
    var selectedTrackOptionsMenu by remember { mutableStateOf<AudioFile?>(null) }
    var showAddToPlaylistMenu by remember { mutableStateOf(false) }
    var targetTrackForPlaylist by remember { mutableStateOf<AudioFile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val rawFavorites = remember(libState.playlists) {
        libState.playlists.find { it.name == "Favorites" }?.tracks ?: emptyList()
    }

    val favorites = remember(rawFavorites, searchQuery, libState.sortOrder, libState.sortDirection) {
        var list = if (searchQuery.isBlank()) {
            rawFavorites
        } else {
            val q = searchQuery.trim().lowercase()
            rawFavorites.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
        when (libState.sortOrder) {
            SortOrder.Title -> list.sortedBy { it.title.lowercase() }
            SortOrder.Artist -> list.sortedBy { it.artist.lowercase() }
            SortOrder.Album -> list.sortedBy { it.album.lowercase() }
            SortOrder.Duration -> list.sortedBy { it.duration }
            SortOrder.DateAdded -> list.sortedBy { it.dateAdded }
            SortOrder.Year -> list.sortedBy { it.year }
        }.let {
            if (libState.sortDirection == SortDirection.Descending) it.reversed() else it
        }
    }

    BackHandler(enabled = isSearchActive || searchQuery.isNotEmpty()) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else {
            isSearchActive = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        TopAppBar(
            title = {
                if (isSearchActive) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search favorites...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (rawFavorites.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "${rawFavorites.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                if (rawFavorites.isNotEmpty()) {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    }

                    IconButton(onClick = { viewModel.toggleLibraryLayout() }) {
                        Icon(
                            imageVector = if (libState.layout == LibraryLayout.List) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                            contentDescription = "Toggle Layout"
                        )
                    }
                }

                IconButton(onClick = { viewModel.switchTab(PlayerViewModel.TAB_SETTINGS) }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings"
                    )
                }
            },
            modifier = Modifier.statusBarsPadding()
        )

        // ── Main Content ─────────────────────────────────────────────────────
        if (rawFavorites.isEmpty()) {
            EmptyFavoritesView(
                onExplore = { viewModel.switchTab(PlayerViewModel.TAB_LIBRARY) }
            )
        } else if (favorites.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching favorite tracks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val isTall = AdaptiveLayout.isTallScreen()
            if (libState.layout == LibraryLayout.List) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isTall) 10.dp else 6.dp)
                ) {
                    item {
                        TracksControlHeader(
                            trackCount = favorites.size,
                            totalDurationMs = favorites.sumOf { it.duration },
                            sortOrder = libState.sortOrder,
                            sortDirection = libState.sortDirection,
                            onPlayAll = { viewModel.playPlaylist(favorites, startIndex = 0, shuffle = false) },
                            onShuffleAll = { viewModel.playPlaylist(favorites, startIndex = 0, shuffle = true) },
                            onToggleSortDirection = { viewModel.toggleSortDirection() }
                        )
                    }

                    items(favorites, key = { it.id }) { track ->
                        val isPlaying = track.contentUri.toString() == playState.mediaId
                        SwipeableTrackRow(
                            onSwipeRight = { viewModel.addToQueue(track) },
                            onSwipeLeft = { viewModel.toggleFavorite(track) },
                            hapticEnabled = hapticEnabled,
                            enabled = swipeGesturesEnabled
                        ) {
                            TrackListItem(
                                track = track,
                                isSelected = libState.selectedTracks.contains(track),
                                isFavorite = true,
                                isPlaying = isPlaying,
                                onClick = { viewModel.playTrack(track, favorites) },
                                onLongClick = { viewModel.toggleSelection(track) },
                                onMoreOptions = { selectedTrackOptionsMenu = track },
                                onToggleFavorite = { viewModel.toggleFavorite(track) }
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTall) 20.dp else 14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(2) }) {
                        TracksControlHeader(
                            trackCount = favorites.size,
                            totalDurationMs = favorites.sumOf { it.duration },
                            sortOrder = libState.sortOrder,
                            sortDirection = libState.sortDirection,
                            onPlayAll = { viewModel.playPlaylist(favorites, startIndex = 0, shuffle = false) },
                            onShuffleAll = { viewModel.playPlaylist(favorites, startIndex = 0, shuffle = true) },
                            onToggleSortDirection = { viewModel.toggleSortDirection() }
                        )
                    }

                    gridItems(favorites, key = { it.id }) { track ->
                        val isPlaying = track.contentUri.toString() == playState.mediaId
                        TrackTileItem(
                            track = track,
                            isSelected = libState.selectedTracks.contains(track),
                            isFavorite = true,
                            isPlaying = isPlaying,
                            onClick = { viewModel.playTrack(track, favorites) },
                            onLongClick = { viewModel.toggleSelection(track) },
                            onMoreOptions = { selectedTrackOptionsMenu = track },
                            onToggleFavorite = { viewModel.toggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }

    // ── Track Options Sheet ──────────────────────────────────────────────────
    selectedTrackOptionsMenu?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isFavorite = true,
            onDismiss = { selectedTrackOptionsMenu = null },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onAddToPlaylist = {
                targetTrackForPlaylist = track
                showAddToPlaylistMenu = true
            },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onGoToArtist = { viewModel.navigateToArtist(track.artist) },
            onGoToAlbum = { viewModel.navigateToAlbum(track.album) },
            onEditMetadata = { showMetadataEditor = track },
            onShowTrackInfo = { showTrackInfo = track }
        )
    }

    // ── Track Info Dialog ────────────────────────────────────────────────────
    showTrackInfo?.let { track ->
        TrackInfoDialog(track = track, onDismiss = { showTrackInfo = null })
    }

    // ── Edit Metadata Dialog ─────────────────────────────────────────────────
    showMetadataEditor?.let { track ->
        MetadataEditorDialog(
            track = track,
            onDismiss = { showMetadataEditor = null },
            onSave = { title, artist, album, genre, year, trackNumber ->
                viewModel.updateTrackMetadata(track, title, artist, album, genre, year, trackNumber)
                showMetadataEditor = null
            }
        )
    }

    // ── Add To Playlist Bottom Sheet ─────────────────────────────────────────
    if (showAddToPlaylistMenu) {
        AddToPlaylistMenu(
            playlists = libState.playlists.filter { !it.isSmart && it.name != "Device Files" },
            onDismiss = {
                showAddToPlaylistMenu = false
                targetTrackForPlaylist = null
            },
            onPlaylistSelected = { playlist ->
                targetTrackForPlaylist?.let { track ->
                    viewModel.addTrackToPlaylist(playlist.name, track)
                }
                showAddToPlaylistMenu = false
                targetTrackForPlaylist = null
            },
            onCreateNewPlaylist = {
                showAddToPlaylistMenu = false
                showCreateDialog = true
            }
        )
    }

    // ── Create Playlist Dialog ───────────────────────────────────────────────
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = {
                showCreateDialog = false
                targetTrackForPlaylist = null
            },
            onCreate = { name ->
                targetTrackForPlaylist?.let { track ->
                    viewModel.createPlaylistWithTracks(name, listOf(track))
                } ?: viewModel.createPlaylist(name)
                showCreateDialog = false
                targetTrackForPlaylist = null
            }
        )
    }
}

@Composable
private fun EmptyFavoritesView(
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "No Favorites Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tap the heart icon or swipe left on any song in your library to add it to your favorites.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onExplore,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Explore Library", fontWeight = FontWeight.Bold)
        }
    }
}
