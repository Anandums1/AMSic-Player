package com.anandu.musicplayer.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.anandu.musicplayer.data.AudioFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val libState by viewModel.libraryState.collectAsState()
    val playState by viewModel.playbackState.collectAsState()
    val hapticEnabled by viewModel.settingsManager.hapticFeedback.collectAsState()
    val swipeGesturesEnabled by viewModel.settingsManager.swipeGesturesEnabled.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistMenu by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showMetadataEditor by remember { mutableStateOf<AudioFile?>(null) }
    var showTrackInfo by remember { mutableStateOf<AudioFile?>(null) }
    var selectedTrackOptionsMenu by remember { mutableStateOf<AudioFile?>(null) }
    var targetTrackForPlaylist by remember { mutableStateOf<AudioFile?>(null) }
    var playlistDialogMode by remember { mutableStateOf("create") } // "create", "create_with_selected", "create_with_target"

    val isSelectionMode by remember { derivedStateOf { libState.selectedTracks.isNotEmpty() } }

    BackHandler(enabled = isSelectionMode || libState.selectedPlaylist != null || libState.searchQuery.isNotEmpty()) {
        when {
            isSelectionMode -> viewModel.clearSelection()
            libState.selectedPlaylist != null -> {
                viewModel.setSearchQuery("")
                viewModel.selectPlaylist(null)
            }
            libState.searchQuery.isNotEmpty() -> viewModel.setSearchQuery("")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isSelectionMode) {
            val isCustomPlaylist = libState.selectedPlaylist != null && !libState.selectedPlaylist!!.isSmart && libState.selectedPlaylist!!.name != "Device Files"
            val currentTracks = when {
                libState.selectedPlaylist != null -> libState.selectedPlaylist!!.tracks
                libState.activeTab == LibraryTab.Tracks -> libState.allSongs
                else -> emptyList()
            }
            val isAllSelected = currentTracks.isNotEmpty() && libState.selectedTracks.size >= currentTracks.size

            SelectionTopBar(
                selectedCount = libState.selectedTracks.size,
                isAllSelected = isAllSelected,
                showAddToPlaylist = !isCustomPlaylist,
                showRemoveFromPlaylist = isCustomPlaylist,
                onToggleSelectAll = {
                    if (isAllSelected) {
                        viewModel.deselectAll()
                    } else {
                        viewModel.selectAll(currentTracks)
                    }
                },
                onPlayNext = {
                    viewModel.playNextBatch(libState.selectedTracks.toList())
                    viewModel.clearSelection()
                },
                onAddToQueue = {
                    viewModel.addTracksToQueue(libState.selectedTracks.toList())
                    viewModel.clearSelection()
                },
                onClear = { viewModel.clearSelection() },
                onAddToPlaylist = { showAddToPlaylistMenu = true },
                onRemoveFromPlaylist = { viewModel.removeSelectedFromPlaylist() },
                onCreateNew = {
                    playlistDialogMode = "create_with_selected"
                    showCreateDialog = true
                },
                modifier = Modifier.statusBarsPadding()
            )
        } else {
            LibraryTopBar(
                title = libState.selectedPlaylist?.name ?: when(libState.activeTab) {
                    LibraryTab.Tracks -> "All Songs"
                    LibraryTab.Playlists -> "Playlists"
                    LibraryTab.Artists -> "Artists"
                    LibraryTab.Albums -> "Albums"
                    LibraryTab.Genres -> "Genres"
                },
                isDetail = libState.selectedPlaylist != null,
                layout = libState.layout,
                sortOrder = libState.sortOrder,
                sortDirection = libState.sortDirection,
                searchQuery = libState.searchQuery,
                searchCategory = libState.searchCategory,
                onSearchCategoryChange = { viewModel.setSearchCategory(it) },
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleLayout = { viewModel.toggleLibraryLayout() },
                onSort = { viewModel.setSortOrder(it) },
                onToggleSortDirection = { viewModel.toggleSortDirection() },
                onCreateClick = {
                    playlistDialogMode = "create"
                    showCreateDialog = true
                },
                onSettingsClick = { viewModel.switchTab(PlayerViewModel.TAB_SETTINGS) },
                onBack = {
                    viewModel.setSearchQuery("")
                    viewModel.selectPlaylist(null)
                },
                modifier = Modifier.statusBarsPadding()
            )
        }

        // Tab Row
        if (!isSelectionMode && libState.selectedPlaylist == null) {
            ScrollableTabRow(
                selectedTabIndex = libState.activeTab.ordinal,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                LibraryTab.entries.forEach { tab ->
                    val tabBadge = when(tab) {
                        LibraryTab.Tracks -> libState.allSongs.size
                        LibraryTab.Playlists -> libState.playlists.size
                        LibraryTab.Artists -> libState.artists.size
                        LibraryTab.Albums -> libState.albums.size
                        LibraryTab.Genres -> libState.genres.size
                    }
                    val label = when(tab) {
                        LibraryTab.Tracks -> "Songs"
                        LibraryTab.Playlists -> "Playlists"
                        LibraryTab.Artists -> "Artists"
                        LibraryTab.Albums -> "Albums"
                        LibraryTab.Genres -> "Genres"
                    }
                    Tab(
                        selected = libState.activeTab == tab,
                        onClick = { viewModel.switchLibraryTab(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    fontWeight = if (libState.activeTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                                if (tabBadge > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "($tabBadge)",
                                        fontSize = 11.sp,
                                        color = if (libState.activeTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        if (playState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AnimatedContent(
                targetState = libState.selectedPlaylist,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { width -> width / 3 } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(tween(220, easing = FastOutLinearInEasing)) { width -> -width / 4 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { width -> -width / 4 } + fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally(tween(220, easing = FastOutLinearInEasing)) { width -> width / 3 } + fadeOut(tween(160)))
                    }
                },
                label = "playlistDetailDrillDown"
            ) { selectedPlaylist ->
                if (selectedPlaylist == null) {
            val query = libState.searchQuery.lowercase()
            val category = libState.searchCategory

            when (libState.activeTab) {
                LibraryTab.Tracks -> {
                    val filteredTracks = remember(libState.allSongs, query, category) {
                        if (query.isEmpty()) {
                            libState.allSongs
                        } else {
                            libState.allSongs.filter {
                                when (category) {
                                    "Tracks" -> it.title.lowercase().contains(query)
                                    "Artists" -> it.artist.lowercase().contains(query)
                                    "Albums" -> it.album.lowercase().contains(query)
                                    else -> it.title.lowercase().contains(query) ||
                                            it.artist.lowercase().contains(query) ||
                                            it.album.lowercase().contains(query)
                                }
                            }
                        }
                    }

                    TracksHub(
                        tracks = filteredTracks,
                        layout = libState.layout,
                        sortOrder = libState.sortOrder,
                        sortDirection = libState.sortDirection,
                        selectedTracks = libState.selectedTracks,
                        favorites = libState.playlists.find { it.name == "Favorites" }?.tracks?.toSet() ?: emptySet(),
                        currentMediaId = playState.mediaId,
                        hapticEnabled = hapticEnabled,
                        swipeGesturesEnabled = swipeGesturesEnabled,
                        onTrackClick = { track ->
                            if (isSelectionMode) {
                                viewModel.toggleSelection(track)
                            } else {
                                viewModel.playTrack(track, filteredTracks)
                            }
                        },
                        onTrackLongClick = { track ->
                            viewModel.toggleSelection(track)
                        },
                        onMoreOptions = { track ->
                            selectedTrackOptionsMenu = track
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayAll = { shuffle ->
                            viewModel.playAll(filteredTracks, shuffle = shuffle)
                        },
                        onSortClick = { /* Handled via TopBar */ },
                        onToggleSortDirection = { viewModel.toggleSortDirection() },
                        onAddToQueue = { viewModel.addToQueue(it) }
                    )
                }

                LibraryTab.Playlists -> {
                    val filteredPlaylists = if (query.isEmpty()) {
                        libState.playlists
                    } else {
                        libState.playlists.filter { it.name.lowercase().contains(query) }
                    }

                    PlaylistHub(
                        smartPlaylists = libState.smartPlaylists,
                        playlists = filteredPlaylists,
                        onPlaylistClick = {
                            viewModel.setSearchQuery("")
                            viewModel.selectPlaylist(it)
                        },
                        onPlaySmartPlaylist = { playlist ->
                            if (playlist.tracks.isNotEmpty()) {
                                viewModel.playPlaylist(playlist.tracks, shuffle = false)
                            }
                        },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onCreatePlaylistClick = {
                            playlistDialogMode = "create"
                            showCreateDialog = true
                        }
                    )
                }

                LibraryTab.Artists -> {
                    val filteredArtists = if (query.isEmpty()) {
                        libState.artists
                    } else {
                        libState.artists.filter { it.key.lowercase().contains(query) }
                    }
                    CategoryHub(
                        items = filteredArtists,
                        icon = Icons.Rounded.Person,
                        onItemClick = { name, tracks ->
                            viewModel.setSearchQuery("")
                            viewModel.selectPlaylist(Playlist(name = name, tracks = tracks))
                        }
                    )
                }

                LibraryTab.Albums -> {
                    val filteredAlbums = if (query.isEmpty()) {
                        libState.albums
                    } else {
                        libState.albums.filter { it.key.lowercase().contains(query) }
                    }
                    CategoryHub(
                        items = filteredAlbums,
                        icon = Icons.Rounded.Album,
                        onItemClick = { name, tracks ->
                            viewModel.setSearchQuery("")
                            viewModel.selectPlaylist(Playlist(name = name, tracks = tracks))
                        }
                    )
                }

                LibraryTab.Genres -> {
                    val filteredGenres = if (query.isEmpty()) {
                        libState.genres
                    } else {
                        libState.genres.filter { it.key.lowercase().contains(query) }
                    }
                    CategoryHub(
                        items = filteredGenres,
                        icon = Icons.Rounded.Category,
                        onItemClick = { name, tracks ->
                            viewModel.setSearchQuery("")
                            viewModel.selectPlaylist(Playlist(name = name, tracks = tracks))
                        }
                    )
                }
            }
        } else {
                    val query = libState.searchQuery.lowercase()
                    val filteredTracks = if (query.isEmpty()) {
                        selectedPlaylist.tracks
                    } else {
                        selectedPlaylist.tracks.filter {
                            it.title.lowercase().contains(query) ||
                            it.artist.lowercase().contains(query) ||
                            it.album.lowercase().contains(query)
                        }
                    }
                    PlaylistDetail(
                        playlist = selectedPlaylist.copy(tracks = filteredTracks),
                        layout = libState.layout,
                        selectedTracks = libState.selectedTracks,
                        favorites = libState.playlists.find { it.name == "Favorites" }?.tracks?.toSet() ?: emptySet(),
                        currentMediaId = playState.mediaId,
                        hapticEnabled = hapticEnabled,
                        swipeGesturesEnabled = swipeGesturesEnabled,
                        onTrackClick = { track ->
                            if (isSelectionMode) {
                                viewModel.toggleSelection(track)
                            } else {
                                viewModel.playTrack(track, filteredTracks)
                            }
                        },
                        onTrackLongClick = { track ->
                            viewModel.toggleSelection(track)
                        },
                        onMoreOptions = { track ->
                            selectedTrackOptionsMenu = track
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayPlaylist = { shuffle ->
                            viewModel.playPlaylist(filteredTracks, shuffle = shuffle)
                        },
                        onSetCustomCoverArt = { uri ->
                            viewModel.setCustomPlaylistCoverArt(selectedPlaylist.id, uri)
                        },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddSongsClick = { showAddSongsDialog = true }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet Context Menu
    if (selectedTrackOptionsMenu != null) {
        val optionsTrack = selectedTrackOptionsMenu!!
        val isFav = libState.playlists.find { it.name == "Favorites" }?.tracks?.any { it.contentUri.toString() == optionsTrack.contentUri.toString() } == true

        TrackOptionsBottomSheet(
            track = optionsTrack,
            isFavorite = isFav,
            onDismiss = { selectedTrackOptionsMenu = null },
            onPlayNext = { viewModel.playNext(optionsTrack) },
            onAddToQueue = { viewModel.addToQueue(optionsTrack) },
            onAddToPlaylist = {
                targetTrackForPlaylist = optionsTrack
                selectedTrackOptionsMenu = null
                showAddToPlaylistMenu = true
            },
            onToggleFavorite = { viewModel.toggleFavorite(optionsTrack) },
            onGoToArtist = { viewModel.navigateToArtist(optionsTrack.artist) },
            onGoToAlbum = { viewModel.navigateToAlbum(optionsTrack.album) },
            onEditMetadata = {
                showMetadataEditor = optionsTrack
                selectedTrackOptionsMenu = null
            },
            onShowTrackInfo = {
                showTrackInfo = optionsTrack
                selectedTrackOptionsMenu = null
            }
        )
    }

    // Technical Audio Specs Dialog
    if (showTrackInfo != null) {
        TrackInfoDialog(
            track = showTrackInfo!!,
            onDismiss = { showTrackInfo = null }
        )
    }

    // Metadata Editor Dialog
    if (showMetadataEditor != null) {
        MetadataEditorDialog(
            track = showMetadataEditor!!,
            onDismiss = { showMetadataEditor = null },
            onSave = { title, artist, album, genre, year, trackNumber ->
                viewModel.updateTrackMetadata(
                    showMetadataEditor!!, title, artist, album, genre, year, trackNumber
                )
                showMetadataEditor = null
            }
        )
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = {
                showCreateDialog = false
                targetTrackForPlaylist = null
            },
            onCreate = { name ->
                if (playlistDialogMode == "create_with_target" && targetTrackForPlaylist != null) {
                    viewModel.createPlaylistWithTracks(name, listOf(targetTrackForPlaylist!!))
                    targetTrackForPlaylist = null
                } else if (playlistDialogMode == "create_with_selected") {
                    viewModel.createPlaylistWithSelected(name)
                } else {
                    viewModel.createPlaylist(name)
                }
                showCreateDialog = false
            }
        )
    }

    // Add to Playlist Menu
    if (showAddToPlaylistMenu) {
        AddToPlaylistMenu(
            playlists = libState.playlists.filter { it.name != "Device Files" && it.name != "Favorites" },
            onDismiss = {
                showAddToPlaylistMenu = false
                targetTrackForPlaylist = null
            },
            onPlaylistSelected = { playlist ->
                if (targetTrackForPlaylist != null) {
                    viewModel.addTrackToPlaylist(playlist.name, targetTrackForPlaylist!!)
                    targetTrackForPlaylist = null
                } else {
                    viewModel.addSelectedToPlaylist(playlist.name)
                }
                showAddToPlaylistMenu = false
            },
            onCreateNewPlaylist = {
                playlistDialogMode = if (targetTrackForPlaylist != null) "create_with_target" else "create_with_selected"
                showCreateDialog = true
                showAddToPlaylistMenu = false
            }
        )
    }

    if (showAddSongsDialog && libState.selectedPlaylist != null) {
        AddSongsToPlaylistDialog(
            playlistName = libState.selectedPlaylist!!.name,
            availableSongs = libState.allSongs,
            onDismiss = { showAddSongsDialog = false },
            onAddSongs = { selectedSongs ->
                viewModel.addTracksToPlaylist(libState.selectedPlaylist!!.name, selectedSongs)
                showAddSongsDialog = false
            }
        )
    }
}

// ── Top Bars ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    showAddToPlaylist: Boolean,
    showRemoveFromPlaylist: Boolean,
    onToggleSelectAll: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onClear: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text("$selectedCount selected", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear Selection")
            }
        },
        actions = {
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    imageVector = if (isAllSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = if (isAllSelected) "Deselect All" else "Select All"
                )
            }
            IconButton(onClick = onPlayNext) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Play Next")
            }
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Rounded.Queue, contentDescription = "Add to Queue")
            }
            if (showRemoveFromPlaylist) {
                IconButton(onClick = onRemoveFromPlaylist) {
                    Icon(Icons.Rounded.PlaylistRemove, contentDescription = "Remove from playlist")
                }
            }
            if (showAddToPlaylist) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
                }
            }
            IconButton(onClick = onCreateNew) {
                Icon(Icons.Rounded.Add, contentDescription = "New playlist with selected")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    title: String,
    isDetail: Boolean,
    layout: LibraryLayout,
    sortOrder: SortOrder,
    sortDirection: SortDirection,
    searchQuery: String,
    searchCategory: String,
    onSearchCategoryChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onToggleSortDirection: () -> Unit,
    onCreateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var localSearchQuery by remember(searchQuery) { mutableStateOf(searchQuery) }

    LaunchedEffect(localSearchQuery) {
        if (localSearchQuery != searchQuery) {
            delay(300.milliseconds)
            onSearchQueryChange(localSearchQuery)
        }
    }

    Column(modifier = modifier) {
        TopAppBar(
            title = {
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        (fadeIn(tween(220)) + expandHorizontally()).togetherWith(
                            fadeOut(tween(160)) + shrinkHorizontally()
                        )
                    },
                    label = "searchBarExpand"
                ) { searching ->
                    if (searching) {
                        TextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("Search songs, artists, albums...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            navigationIcon = {
                if (isSearchActive) {
                    IconButton(onClick = {
                        isSearchActive = false
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Stop Search")
                    }
                } else if (isDetail) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                if (!isSearchActive) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onToggleLayout) {
                        Icon(
                            imageVector = if (layout == LibraryLayout.List) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.List,
                            contentDescription = "Toggle Layout"
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = when (order) {
                                                    SortOrder.Title -> "Title"
                                                    SortOrder.Artist -> "Artist"
                                                    SortOrder.Album -> "Album"
                                                    SortOrder.Duration -> "Duration"
                                                    SortOrder.DateAdded -> "Date Added"
                                                    SortOrder.Year -> "Year"
                                                },
                                                fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (sortOrder == order) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = if (sortDirection == SortDirection.Ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSort(order)
                                        showSortMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (sortDirection == SortDirection.Ascending) "Switch to Descending ↓" else "Switch to Ascending ↑")
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.SwapVert, contentDescription = null)
                                },
                                onClick = {
                                    onToggleSortDirection()
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                    IconButton(onClick = onCreateClick) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create Playlist")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                } else {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            localSearchQuery = ""
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear Search")
                        }
                    }
                }
            }
        )

        // Search category filter chips
        if (isSearchActive) {
            val categories = listOf("All", "Tracks", "Playlists", "Artists", "Albums")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = searchCategory == cat,
                        onClick = { onSearchCategoryChange(cat) },
                        label = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

// ── Hubs ───────────────────────────────────────────────────────────────

@Composable
private fun TracksHub(
    tracks: List<AudioFile>,
    layout: LibraryLayout,
    sortOrder: SortOrder,
    sortDirection: SortDirection,
    selectedTracks: Set<AudioFile>,
    favorites: Set<AudioFile>,
    currentMediaId: String,
    hapticEnabled: Boolean,
    swipeGesturesEnabled: Boolean = true,
    onTrackClick: (AudioFile) -> Unit,
    onTrackLongClick: (AudioFile) -> Unit,
    onMoreOptions: (AudioFile) -> Unit,
    onToggleFavorite: (AudioFile) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onSortClick: () -> Unit,
    onToggleSortDirection: () -> Unit,
    onAddToQueue: (AudioFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTall = AdaptiveLayout.isTallScreen()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.MusicOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No songs found in your library",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            if (layout == LibraryLayout.List) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 36.dp, // Space for the alphabet scrubber
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (isTall) 10.dp else 6.dp)
                ) {
                    item {
                        TracksControlHeader(
                            trackCount = tracks.size,
                            totalDurationMs = tracks.sumOf { it.duration },
                            sortOrder = sortOrder,
                            sortDirection = sortDirection,
                            onPlayAll = { onPlayAll(false) },
                            onShuffleAll = { onPlayAll(true) },
                            onToggleSortDirection = onToggleSortDirection
                        )
                    }

                    items(tracks, key = { it.id }) { track ->
                        val isPlaying = track.contentUri.toString() == currentMediaId
                        SwipeableTrackRow(
                            onSwipeRight = { onAddToQueue(track) },
                            onSwipeLeft = { onToggleFavorite(track) },
                            hapticEnabled = hapticEnabled,
                            enabled = swipeGesturesEnabled
                        ) {
                            TrackListItem(
                                track = track,
                                isSelected = selectedTracks.contains(track),
                                isFavorite = favorites.any { it.contentUri.toString() == track.contentUri.toString() },
                                isPlaying = isPlaying,
                                onClick = { onTrackClick(track) },
                                onLongClick = { onTrackLongClick(track) },
                                onMoreOptions = { onMoreOptions(track) },
                                onToggleFavorite = { onToggleFavorite(track) }
                            )
                        }
                    }
                }

                // Fast Alphabet Scrubber on the right edge
                AlphabetScrubber(
                    onLetterSelected = { letter ->
                        coroutineScope.launch {
                            val targetIndex = if (letter == '#') {
                                0
                            } else {
                                val match = tracks.indexOfFirst {
                                    val key = if (sortOrder == SortOrder.Artist) it.artist else it.title
                                    key.trim().startsWith(letter, ignoreCase = true)
                                }
                                if (match >= 0) match + 1 else -1 // +1 for the header
                            }
                            if (targetIndex >= 0) {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    },
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
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
                            trackCount = tracks.size,
                            totalDurationMs = tracks.sumOf { it.duration },
                            sortOrder = sortOrder,
                            sortDirection = sortDirection,
                            onPlayAll = { onPlayAll(false) },
                            onShuffleAll = { onPlayAll(true) },
                            onToggleSortDirection = onToggleSortDirection
                        )
                    }

                    gridItems(tracks, key = { it.id }) { track ->
                        val isPlaying = track.contentUri.toString() == currentMediaId
                        TrackTileItem(
                            track = track,
                            isSelected = selectedTracks.contains(track),
                            isFavorite = favorites.any { it.contentUri.toString() == track.contentUri.toString() },
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) },
                            onLongClick = { onTrackLongClick(track) },
                            onMoreOptions = { onMoreOptions(track) },
                            onToggleFavorite = { onToggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TracksControlHeader(
    trackCount: Int,
    totalDurationMs: Long,
    sortOrder: SortOrder,
    sortDirection: SortDirection,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onToggleSortDirection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatPlaylistSummary(trackCount, totalDurationMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            // Quick Sort Chip
            FilterChip(
                selected = false,
                onClick = onToggleSortDirection,
                label = {
                    Text(
                        text = "${sortOrder.name} ${if (sortDirection == SortDirection.Ascending) "↑" else "↓"}",
                        fontSize = 11.sp
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Reverse Sort Order",
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Play All & Shuffle All action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play All", fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = onShuffleAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shuffle", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Playlists Hub with Smart Playlists ─────────────────────────────────

@Composable
private fun PlaylistHub(
    smartPlaylists: List<Playlist>,
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaySmartPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onCreatePlaylistClick: () -> Unit
) {
    val isTall = AdaptiveLayout.isTallScreen()
    val userPlaylists = remember(playlists) {
        playlists.filter { it.name != "Device Files" && it.name != "Favorites" }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = if (isTall) 16.dp else 12.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTall) 20.dp else 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Smart Playlists Section
        if (smartPlaylists.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                SmartPlaylistsSection(
                    smartPlaylists = smartPlaylists,
                    onPlaylistClick = onPlaylistClick,
                    onPlayClick = onPlaySmartPlaylist
                )
            }

            item(span = { GridItemSpan(2) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Playlists (${userPlaylists.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onCreatePlaylistClick) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Playlist")
                    }
                }
            }
        }

        gridItems(userPlaylists, key = { it.id }) { playlist ->
            PlaylistTile(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
                onDelete = { onDeletePlaylist(playlist) }
            )
        }
    }
}

@Composable
private fun SmartPlaylistsSection(
    smartPlaylists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onPlayClick: (Playlist) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Smart Mixes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(smartPlaylists, key = { it.name }) { playlist ->
                SmartPlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onPlay = { onPlayClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun SmartPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val (gradientColors, icon) = when (playlist.name) {
        "Favorites" -> listOf(Color(0xFFE91E63), Color(0xFF9C27B0)) to Icons.Rounded.Favorite
        "Most Listened" -> listOf(Color(0xFF009688), Color(0xFF3F51B5)) to Icons.Rounded.Headphones
        "Recently Added" -> listOf(Color(0xFFFF9800), Color(0xFFF44336)) to Icons.Rounded.AutoAwesome
        "Recently Played" -> listOf(Color(0xFF2196F3), Color(0xFF673AB7)) to Icons.Rounded.History
        else -> listOf(Color(0xFF607D8B), Color(0xFF37474F)) to Icons.AutoMirrored.Rounded.PlaylistPlay
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.tracks.size} songs",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play Mix",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── Tiles & List Items ────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTile(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isDefault = playlist.name == "Device Files" || playlist.isSmart

    val distinctAlbumArts = remember(playlist.tracks) {
        playlist.tracks.mapNotNull { it.albumArtUri }.distinct().take(4)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (!isDefault) showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.customCoverArtUri != null) {
                AsyncImage(
                    model = playlist.customCoverArtUri,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (distinctAlbumArts.size >= 4) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = distinctAlbumArts[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        AsyncImage(model = distinctAlbumArts[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = distinctAlbumArts[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        AsyncImage(model = distinctAlbumArts[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else if (distinctAlbumArts.isNotEmpty()) {
                AsyncImage(
                    model = distinctAlbumArts[0],
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatPlaylistSummary(playlist.tracks.size, playlist.tracks.sumOf { it.duration }),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            if (showMenu) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackListItem(
    track: AudioFile,
    isSelected: Boolean,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreOptions: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
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
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerAnimation(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                }
            } else if (track.albumArtUri == null || isError) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onMoreOptions) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackTileItem(
    track: AudioFile,
    isSelected: Boolean,
    isFavorite: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreOptions: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
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
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerAnimation(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                }
            } else if (track.albumArtUri == null || isError) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            modifier = Modifier.size(16.dp),
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onMoreOptions,
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Playlist Detail ───────────────────────────────────────────────────

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    layout: LibraryLayout,
    selectedTracks: Set<AudioFile>,
    favorites: Set<AudioFile>,
    currentMediaId: String,
    hapticEnabled: Boolean,
    swipeGesturesEnabled: Boolean = true,
    onTrackClick: (AudioFile) -> Unit,
    onTrackLongClick: (AudioFile) -> Unit,
    onMoreOptions: (AudioFile) -> Unit,
    onToggleFavorite: (AudioFile) -> Unit,
    onPlayPlaylist: (Boolean) -> Unit,
    onSetCustomCoverArt: (Uri) -> Unit,
    onAddToQueue: (AudioFile) -> Unit,
    onAddSongsClick: () -> Unit
) {
    val isTall = AdaptiveLayout.isTallScreen()
    if (playlist.tracks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            PlaylistHeader(
                playlist = playlist,
                onPlay = {},
                onShuffle = {},
                onSetCustomCoverArt = onSetCustomCoverArt
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "No songs in this playlist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onAddSongsClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Songs", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            if (layout == LibraryLayout.List) {
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
                        PlaylistHeader(
                            playlist = playlist,
                            onPlay = { onPlayPlaylist(false) },
                            onShuffle = { onPlayPlaylist(true) },
                            onSetCustomCoverArt = onSetCustomCoverArt
                        )
                    }
                    items(playlist.tracks, key = { it.id }) { track ->
                        val isPlaying = track.contentUri.toString() == currentMediaId
                        SwipeableTrackRow(
                            onSwipeRight = { onAddToQueue(track) },
                            onSwipeLeft = { onToggleFavorite(track) },
                            hapticEnabled = hapticEnabled,
                            enabled = swipeGesturesEnabled
                        ) {
                            TrackListItem(
                                track = track,
                                isSelected = selectedTracks.contains(track),
                                isFavorite = favorites.any { it.contentUri.toString() == track.contentUri.toString() },
                                isPlaying = isPlaying,
                                onClick = { onTrackClick(track) },
                                onLongClick = { onTrackLongClick(track) },
                                onMoreOptions = { onMoreOptions(track) },
                                onToggleFavorite = { onToggleFavorite(track) }
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
                        PlaylistHeader(
                            playlist = playlist,
                            onPlay = { onPlayPlaylist(false) },
                            onShuffle = { onPlayPlaylist(true) },
                            onSetCustomCoverArt = onSetCustomCoverArt
                        )
                    }
                    gridItems(playlist.tracks, key = { it.id }) { track ->
                        val isPlaying = track.contentUri.toString() == currentMediaId
                        TrackTileItem(
                            track = track,
                            isSelected = selectedTracks.contains(track),
                            isFavorite = favorites.any { it.contentUri.toString() == track.contentUri.toString() },
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) },
                            onLongClick = { onTrackLongClick(track) },
                            onMoreOptions = { onMoreOptions(track) },
                            onToggleFavorite = { onToggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSetCustomCoverArt: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onSetCustomCoverArt(uri)
        }
    }

    val distinctAlbumArts = remember(playlist.tracks) {
        playlist.tracks.mapNotNull { it.albumArtUri }.distinct().take(4)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        photoPickerLauncher.launch("image/*")
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.customCoverArtUri != null) {
                AsyncImage(
                    model = playlist.customCoverArtUri,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (distinctAlbumArts.size >= 4) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = distinctAlbumArts[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        AsyncImage(model = distinctAlbumArts[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = distinctAlbumArts[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        AsyncImage(model = distinctAlbumArts[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            } else if (distinctAlbumArts.isNotEmpty()) {
                AsyncImage(
                    model = distinctAlbumArts[0],
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatPlaylistSummary(playlist.tracks.size, playlist.tracks.sumOf { it.duration }),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                Spacer(Modifier.width(8.dp))
                Text("Play", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }

            FilledTonalButton(
                onClick = onShuffle,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle")
                Spacer(Modifier.width(8.dp))
                Text("Shuffle", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Category Hub (Artists, Albums, Genres) ─────────────────────────────

@Composable
private fun CategoryHub(
    items: Map<String, List<AudioFile>>,
    icon: ImageVector,
    onItemClick: (String, List<AudioFile>) -> Unit
) {
    val isTall = AdaptiveLayout.isTallScreen()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = if (isTall) 20.dp else 14.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTall) 20.dp else 14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItems(items.toList(), key = { it.first }) { (name, tracks) ->
            CategoryTile(
                name = name,
                tracks = tracks,
                icon = icon,
                onClick = { onItemClick(name, tracks) }
            )
        }
    }
}

@Composable
private fun CategoryTile(
    name: String,
    tracks: List<AudioFile>,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatPlaylistSummary(tracks.size, tracks.sumOf { it.duration }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Dialogs ────────────────────────────────────────────────────────────

@Composable
internal fun MetadataEditorDialog(
    track: AudioFile,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, year: Int, trackNumber: String) -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var genre by remember { mutableStateOf(track.genre) }
    var yearStr by remember { mutableStateOf(if (track.year > 0) track.year.toString() else "") }
    var trackNum by remember { mutableStateOf(track.trackNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Metadata") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = yearStr,
                        onValueChange = { yearStr = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = trackNum,
                        onValueChange = { trackNum = it },
                        label = { Text("Track No.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val yearInt = yearStr.toIntOrNull() ?: 0
                    onSave(title, artist, album, genre, yearInt, trackNum)
                },
                enabled = title.isNotBlank() && artist.isNotBlank() && album.isNotBlank()
            ) {
                Text("Save")
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
internal fun AddToPlaylistMenu(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("Create New Playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onCreateNewPlaylist() }
                    )
                    HorizontalDivider()
                }
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            text = "No custom playlists found.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text(formatPlaylistSummary(playlist.tracks.size, playlist.tracks.sumOf { it.duration })) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, contentDescription = null) },
                            modifier = Modifier.clickable { onPlaylistSelected(playlist) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Playlist") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatPlaylistSummary(songCount: Int, totalDurationMs: Long): String {
    if (songCount == 0) return "0 songs"

    val totalSeconds = totalDurationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val durationText = when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }

    val songText = if (songCount == 1) "1 song" else "$songCount songs"
    return "$songText • $durationText"
}

@Composable
private fun AddSongsToPlaylistDialog(
    playlistName: String,
    availableSongs: List<AudioFile>,
    onDismiss: () -> Unit,
    onAddSongs: (List<AudioFile>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSongs by remember { mutableStateOf(setOf<AudioFile>()) }

    val filteredSongs = remember(availableSongs, searchQuery) {
        if (searchQuery.isBlank()) availableSongs
        else availableSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    val isAllSelected = remember(filteredSongs, selectedSongs) {
        filteredSongs.isNotEmpty() && selectedSongs.containsAll(filteredSongs)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Add Songs to $playlistName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${selectedSongs.size} songs selected",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search songs...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    FilterChip(
                        selected = isAllSelected,
                        onClick = {
                            selectedSongs = if (isAllSelected) {
                                selectedSongs - filteredSongs.toSet()
                            } else {
                                selectedSongs + filteredSongs.toSet()
                            }
                        },
                        label = { Text(if (isAllSelected) "Clear All" else "Select All", fontSize = 11.sp) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (filteredSongs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No songs found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredSongs, key = { it.id }) { track ->
                            val isChecked = selectedSongs.contains(track)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedSongs = if (isChecked) selectedSongs - track else selectedSongs + track
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedSongs = if (checked) selectedSongs + track else selectedSongs - track
                                    }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddSongs(selectedSongs.toList())
                },
                enabled = selectedSongs.isNotEmpty()
            ) {
                Text("Add (${selectedSongs.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
