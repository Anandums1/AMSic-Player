package com.anandu.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import androidx.compose.runtime.Immutable
import android.app.PendingIntent
import com.anandu.musicplayer.data.AudioFile
import com.anandu.musicplayer.data.MediaStoreDataSource
import com.anandu.musicplayer.data.SettingsManager
import com.anandu.musicplayer.data.MetadataUpdateResult
import com.anandu.musicplayer.data.api.LyricsProvider
import com.anandu.musicplayer.data.db.PlaylistDao
import com.anandu.musicplayer.data.db.PlaylistEntity
import com.anandu.musicplayer.data.db.PlaylistSongEntity
import com.anandu.musicplayer.data.db.SongStatsDao
import com.anandu.musicplayer.data.db.SongStatsEntity
import com.anandu.musicplayer.player.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import androidx.core.net.toUri

enum class LibraryLayout { List, Tiles }
enum class SortOrder { Title, Artist, Album, Duration, DateAdded, Year }
enum class SortDirection { Ascending, Descending }
enum class LibraryTab { Tracks, Playlists, Artists, Albums, Genres }
@Immutable
data class Playlist(val id: Long = 0, val name: String, val tracks: List<AudioFile>, val customCoverArtUri: Uri? = null, val isSmart: Boolean = false)

/**
 * ViewModel that bridges the UI with the [PlaybackService]'s [MediaController].
 *
 * - Connects to the service on init
 * - Loads tracks from MediaStore
 * - Feeds playback state back to the Compose UI via [StateFlow]s
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val playlistDao: PlaylistDao,
    private val songStatsDao: SongStatsDao,
    val settingsManager: SettingsManager,
) : AndroidViewModel(application) {

    companion object {
        const val TAB_LIBRARY = 0
        const val TAB_FAVORITES = 1
        const val TAB_SETTINGS = 2

        // Constants for custom commands (matching PlaybackService)
        const val ACTION_SET_SLEEP_TIMER = "com.anandu.musicplayer.SET_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.anandu.musicplayer.CANCEL_SLEEP_TIMER"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"
        const val EXTRA_REMAINING_MS = "extra_remaining_ms"
    }

    // ── State exposed to UI ────────────────────────────────────────────
    @Immutable
    data class PlaybackState(
        val isPlaying: Boolean = false,
        val title: String = "",
        val artist: String = "",
        val albumArtUri: String? = null,
        val mediaId: String = "",
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val isLoading: Boolean = false,
        val isNowPlayingOpen: Boolean = false,
        val currentTab: Int = TAB_LIBRARY,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val shuffleModeEnabled: Boolean = false,
        val dominantColor: Int = 0xFF1C1B1F.toInt(), // Default dark color
        val isFavorite: Boolean = false,
        val sleepTimerRemainingMs: Long = 0L,
        val plainLyrics: String? = null,
        val syncedLyrics: List<LyricLine>? = null,
        val isLyricsLoading: Boolean = false,
        val pendingMetadataUpdate: PendingIntent? = null,
        val queue: List<AudioFile> = emptyList(),
        val bitrateBps: Int = 0,
    )

    private data class PendingUpdate(
        val track: AudioFile,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String = "",
        val year: Int = 0,
        val trackNumber: String = ""
    )

    private var pendingUpdate: PendingUpdate? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    @Immutable
    data class ProgressState(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
    )

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState: StateFlow<ProgressState> = _progressState.asStateFlow()

    @Immutable
    data class LibraryState(
        val layout: LibraryLayout = LibraryLayout.List,
        val sortOrder: SortOrder = SortOrder.Title,
        val sortDirection: SortDirection = SortDirection.Ascending,
        val activeTab: LibraryTab = LibraryTab.Tracks,
        val allSongs: List<AudioFile> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val smartPlaylists: List<Playlist> = emptyList(),
        val artists: Map<String, List<AudioFile>> = emptyMap(),
        val albums: Map<String, List<AudioFile>> = emptyMap(),
        val genres: Map<String, List<AudioFile>> = emptyMap(),
        val selectedPlaylist: Playlist? = null,
        val selectedTracks: Set<AudioFile> = emptySet(),
        val searchQuery: String = "",
        val searchCategory: String = "All",
    )

    private val _libraryState = MutableStateFlow(LibraryState())
    val libraryState: StateFlow<LibraryState> = _libraryState.asStateFlow()

    private val _tracks = MutableStateFlow<List<AudioFile>>(emptyList())
    val tracks: StateFlow<List<AudioFile>> = _tracks.asStateFlow()

    private var originalTracks: List<AudioFile> = emptyList()

    private var mediaController: MediaController? = null

    private val prefs = application.getSharedPreferences("playback_session", Context.MODE_PRIVATE)

    // Cached Coil ImageLoader — reused across palette extractions to avoid leaking caches
    private val imageLoader = ImageLoader(application)

    // Cached favorites URI set — avoids scanning the playlist on every syncState call
    private var cachedFavUris: Set<String> = emptySet()

    // Lyrics in-memory LRU cache — avoids re-fetching on song revisit
    private val lyricsCache = object : LinkedHashMap<String, Pair<String?, List<LyricLine>?>>(20, 0.75f, true) {
        @Suppress("UNUSED_PARAMETER")
        override fun removeEldestEntry(eldest: Map.Entry<String, Pair<String?, List<LyricLine>?>>): Boolean {
            return size > 50
        }
    }

    // Throttle saveSession to at most once per 5 seconds
    private var lastSaveTimeMs = 0L

    // Track whether position polling is active (stop when paused to save battery)
    private var isPolling = false



    // Handler to periodically sync the position while playing
    private val handler = Handler(Looper.getMainLooper())
    private var lastListenCheckTimeMs = 0L
    private var accumulatedListenTimeMs = 0L

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            mediaController?.let { ctrl ->
                val currentPos = ctrl.currentPosition
                val currentDur = ctrl.duration.coerceAtLeast(0L)
                _progressState.value = ProgressState(
                    positionMs = currentPos,
                    durationMs = currentDur,
                )

                if (ctrl.isPlaying) {
                    val now = System.currentTimeMillis()
                    if (lastListenCheckTimeMs > 0L) {
                        val delta = (now - lastListenCheckTimeMs).coerceIn(0L, 2000L)
                        accumulatedListenTimeMs += delta
                        if (accumulatedListenTimeMs >= 4000L) {
                            val uri = ctrl.currentMediaItem?.mediaId
                            if (uri != null) {
                                val flushTime = accumulatedListenTimeMs
                                accumulatedListenTimeMs = 0L
                                viewModelScope.launch(Dispatchers.IO) {
                                    recordListenTime(uri, flushTime)
                                }
                            }
                        }
                    }
                    lastListenCheckTimeMs = now
                } else {
                    lastListenCheckTimeMs = 0L
                }
            }
            handler.postDelayed(this, 250L)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────
    init {
        connectToService()
        observePlaylists()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsManager.excludeShortClips.collectLatest {
                loadTracks()
            }
        }
    }

    private fun observePlaylists() {
        // Ensure "Favorites" playlist exists in a single background task to avoid re-entrant Flow loops
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = playlistDao.getAllPlaylistSongs()
                // Check if Favorites playlist exists via DAO query
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            // First time seed
            withContext(Dispatchers.IO) {
                try {
                    val currentPlaylists = playlistDao.getAllPlaylistSongs()
                    // Safe check & seed Favorites if missing
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            playlistDao.getAllPlaylists().collectLatest { entities ->
                // Ensure Favorites exists safely
                if (entities.none { it.name == "Favorites" }) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                val deviceFiles = _libraryState.value.playlists.find { it.name == "Device Files" }

                val customPlaylists = entities.map { entity ->
                    // We'll populate tracks once we have originalTracks
                    Playlist(id = entity.id, name = entity.name, tracks = emptyList())
                }
                
                _libraryState.update {
                    it.copy(playlists = (if (deviceFiles != null) listOf(deviceFiles) else emptyList()) + customPlaylists)
                }
                
                // If originalTracks is already loaded, populate these playlists
                if (originalTracks.isNotEmpty()) {
                    populatePlaylistsWithTracks()
                }
            }
        }
    }

    private fun populatePlaylistsWithTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            val allMappings = playlistDao.getAllPlaylistSongs()
            val currentLib = _libraryState.value
            
            val updatedPlaylists = currentLib.playlists.map { playlist ->
                if (playlist.name == "Device Files") return@map playlist
                
                val trackUris = allMappings
                    .filter { it.playlistId == playlist.id }
                    .map { it.trackUri }
                
                val tracks = originalTracks.filter { it.contentUri.toString() in trackUris }
                playlist.copy(tracks = tracks)
            }
            
            // Update cached favorites URI set for fast lookups in syncState
            cachedFavUris = updatedPlaylists
                .find { it.name == "Favorites" }
                ?.tracks?.map { it.contentUri.toString() }?.toSet() ?: emptySet()

            _libraryState.update {
                it.copy(
                    playlists = updatedPlaylists,
                    selectedPlaylist = it.selectedPlaylist?.let { sel ->
                        updatedPlaylists.find { it.id == sel.id || it.name == sel.name }
                    }
                )
            }
            refreshSmartPlaylists()
        }
    }

    private fun connectToService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) {
                    syncState(controller)
                }
            })
            .buildAsync()

        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()
                mediaController = controller

                // Listen for state changes from the player
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        syncState(controller)
                    }

                    override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                        syncState(controller)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncState(controller)
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        syncState(controller)
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        syncState(controller)
                    }

                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        syncState(controller)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncState(controller)
                        mediaItem?.mediaId?.let { uri ->
                            onTrackStarted(uri)
                        }
                    }
                })

                // Start position polling
                handler.post(positionUpdateRunnable)

                // Load tracks and auto-play the first one
                loadTracks()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun onTrackStarted(trackUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = songStatsDao.getSongStats(trackUri)
            val updated = if (existing != null) {
                existing.copy(
                    playCount = existing.playCount + 1,
                    lastPlayedTimestamp = System.currentTimeMillis()
                )
            } else {
                SongStatsEntity(
                    trackUri = trackUri,
                    playCount = 1,
                    totalTimeListenedMs = 0L,
                    lastPlayedTimestamp = System.currentTimeMillis()
                )
            }
            songStatsDao.upsertSongStats(updated)
            refreshSmartPlaylists()
        }
    }

    private suspend fun recordListenTime(trackUri: String, timeMs: Long) {
        val existing = songStatsDao.getSongStats(trackUri)
        if (existing != null) {
            songStatsDao.upsertSongStats(
                existing.copy(
                    totalTimeListenedMs = existing.totalTimeListenedMs + timeMs,
                    lastPlayedTimestamp = System.currentTimeMillis()
                )
            )
        } else {
            songStatsDao.upsertSongStats(
                SongStatsEntity(
                    trackUri = trackUri,
                    totalTimeListenedMs = timeMs,
                    lastPlayedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun refreshSmartPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            if (originalTracks.isEmpty()) return@launch

            val trackMap = originalTracks.associateBy { it.contentUri.toString() }

            // 1. Favorites
            val favTracks = originalTracks.filter { it.contentUri.toString() in cachedFavUris }
            val favPlaylist = Playlist(id = -1L, name = "Favorites", tracks = favTracks, isSmart = true)

            // 2. Most Listened (ordered by totalTimeListenedMs, then playCount)
            val mostPlayedStats = songStatsDao.getMostPlayed(50)
            val mostListenedTracks = mostPlayedStats.mapNotNull { trackMap[it.trackUri] }
            val mostListenedPlaylist = Playlist(id = -2L, name = "Most Listened", tracks = mostListenedTracks, isSmart = true)

            // 3. Recently Added (newest 50 tracks)
            val recentlyAddedTracks = originalTracks.sortedByDescending { it.dateAdded }.take(50)
            val recentlyAddedPlaylist = Playlist(id = -3L, name = "Recently Added", tracks = recentlyAddedTracks, isSmart = true)

            // 4. Recently Played (ordered by lastPlayedTimestamp)
            val recentlyPlayedStats = songStatsDao.getRecentlyPlayed(50)
            val recentlyPlayedTracks = recentlyPlayedStats.mapNotNull { trackMap[it.trackUri] }
            val recentlyPlayedPlaylist = Playlist(id = -4L, name = "Recently Played", tracks = recentlyPlayedTracks, isSmart = true)

            val smartList = listOf(favPlaylist, mostListenedPlaylist, recentlyAddedPlaylist, recentlyPlayedPlaylist)
            _libraryState.update { it.copy(smartPlaylists = smartList) }
        }
    }

    private fun AudioFile.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(contentUri.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri)
                    .build()
            )
            .build()

    private fun List<AudioFile>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

    private fun updatePlayerQueue(tracks: List<AudioFile>) {
        val controller = mediaController ?: return
        val mediaItems = tracks.toMediaItems()

        handler.post {
            if (controller.mediaItemCount == 0) {
                controller.setMediaItems(mediaItems)
                controller.prepare()
            } else {
                val currentIndex = controller.currentMediaItemIndex
                val currentPosition = controller.currentPosition
                
                // Find if the current playing item is in the new list to maintain playback
                val currentMediaId = controller.currentMediaItem?.mediaId
                val newIndex = if (currentMediaId != null) {
                    tracks.indexOfFirst { it.contentUri.toString() == currentMediaId }
                } else {
                    -1
                }

                if (newIndex != -1) {
                    controller.setMediaItems(mediaItems, newIndex, currentPosition)
                } else {
                    controller.setMediaItems(mediaItems, currentIndex, currentPosition)
                }
            }
        }
    }

    /**
     * Queries MediaStore for audio files and updates the player's playlist.
     */
    fun loadTracks() {
        if (mediaController == null) return
        
        viewModelScope.launch {
            // State mutation on Main thread
            _playbackState.update { it.copy(isLoading = true) }
            
            val (files, savedShuffle, savedRepeat, lastTrackUri) = withContext(Dispatchers.IO) {
                var queriedFiles = mediaStoreDataSource.queryAudioFiles()
                if (settingsManager.excludeShortClips.value) {
                    queriedFiles = queriedFiles.filter { it.duration >= 30000 }
                }
                val shuffle = prefs?.getBoolean("shuffle_mode", false) ?: false
                val repeat = prefs?.getInt("repeat_mode", Player.REPEAT_MODE_OFF) ?: Player.REPEAT_MODE_OFF
                val uri = prefs?.getString("last_track_uri", null)
                Quadruple(queriedFiles, shuffle, repeat, uri)
            }
            
            originalTracks = files
            val sortedFiles = sortTrackList(files, _libraryState.value.sortOrder, _libraryState.value.sortDirection)
            
            // Group data
            val artists = files.groupBy { it.artist }
            val albums = files.groupBy { it.album }
            val genres = files.groupBy { it.genre.ifBlank { "Unknown Genre" } }

            // Create "Device Files" playlist
            val devicePlaylist = Playlist(name = "Device Files", tracks = files)
            _libraryState.update { state ->
                state.copy(
                    allSongs = sortedFiles,
                    playlists = listOf(devicePlaylist) + state.playlists.filter { it.name != "Device Files" },
                    artists = artists,
                    albums = albums,
                    genres = genres
                )
            }
            
            // Populate custom playlists since originalTracks is now available
            populatePlaylistsWithTracks()
            refreshSmartPlaylists()
            
            // Use saved shuffle mode for initial list
            val currentTracks = if (savedShuffle) {
                files.shuffled(java.util.Random(42))
            } else {
                files
            }
            
            _tracks.value = currentTracks
            _playbackState.update {
                it.copy(
                    shuffleModeEnabled = savedShuffle,
                    repeatMode = savedRepeat
                )
            }

            if (currentTracks.isNotEmpty()) {
                val controller = mediaController ?: return@launch
                val mediaItems = currentTracks.toMediaItems()

                if (controller.mediaItemCount == 0) {
                    controller.setMediaItems(mediaItems)
                    controller.repeatMode = savedRepeat
                    controller.prepare()
                    
                    val lastPosition = withContext(Dispatchers.IO) {
                        prefs?.getLong("last_position", 0L) ?: 0L
                    }
                    
                    if (lastTrackUri != null) {
                        val index = currentTracks.indexOfFirst { it.contentUri.toString() == lastTrackUri }
                        if (index != -1) {
                            controller.seekTo(index, lastPosition)
                        }
                    }
                } else {
                    val currentIndex = controller.currentMediaItemIndex
                    val currentPosition = controller.currentPosition
                    controller.setMediaItems(mediaItems, currentIndex, currentPosition)
                }
            }
            _playbackState.update { it.copy(isLoading = false) }
        }
    }

    private data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )


    private fun syncState(controller: MediaController) {
        val meta = controller.mediaMetadata
        val oldArtUri = _playbackState.value.albumArtUri
        val newArtUri = meta.artworkUri?.toString()
        val currentMediaId = controller.currentMediaItem?.mediaId ?: ""
        val oldMediaId = _playbackState.value.mediaId

        val isFav = currentMediaId in cachedFavUris

        val remainingSleepMs = controller.sessionExtras.getLong(EXTRA_REMAINING_MS, 0L)

        val currentAudioFile = originalTracks.find { it.contentUri.toString() == currentMediaId }
        val activeBitrate = currentAudioFile?.bitrateBps ?: 0

        val trackMap = originalTracks.associateBy { it.contentUri.toString() }
        val currentQueue = mutableListOf<AudioFile>()
        for (i in 0 until controller.mediaItemCount) {
            val item = controller.getMediaItemAt(i)
            val itemMeta = item.mediaMetadata
            val itemUri = item.localConfiguration?.uri ?: item.mediaId.toUri()
            val uriString = item.mediaId.ifEmpty { itemUri.toString() }
            val matchedTrack = trackMap[uriString]
            if (matchedTrack != null) {
                currentQueue.add(matchedTrack)
            } else {
                currentQueue.add(
                    AudioFile(
                        id = uriString.hashCode().toLong(),
                        title = itemMeta.title?.toString() ?: "Unknown",
                        artist = itemMeta.artist?.toString() ?: "Unknown Artist",
                        album = itemMeta.albumTitle?.toString() ?: "Unknown Album",
                        duration = 0L,
                        contentUri = itemUri,
                        albumArtUri = itemMeta.artworkUri
                    )
                )
            }
        }

        val currentPos = controller.currentPosition
        val currentDur = controller.duration.coerceAtLeast(0L)
        _progressState.value = ProgressState(positionMs = currentPos, durationMs = currentDur)

        _playbackState.update {
            it.copy(
                isPlaying = controller.isPlaying,
                title = meta.title?.toString() ?: "Unknown",
                artist = meta.artist?.toString() ?: "Unknown Artist",
                albumArtUri = newArtUri,
                mediaId = currentMediaId,
                positionMs = currentPos,
                durationMs = currentDur,
                repeatMode = controller.repeatMode,
                shuffleModeEnabled = controller.shuffleModeEnabled,
                isFavorite = isFav,
                sleepTimerRemainingMs = remainingSleepMs,
                queue = currentQueue,
                bitrateBps = activeBitrate,
            )
        }

        if (controller.isPlaying) {
            if (!isPolling) {
                isPolling = true
                handler.post(positionUpdateRunnable)
            }
        } else {
            isPolling = false
            handler.removeCallbacks(positionUpdateRunnable)
        }

        if (currentMediaId != oldMediaId && currentMediaId.isNotEmpty()) {
            fetchLyrics(meta.title?.toString() ?: "", meta.artist?.toString() ?: "")
        }

        if (newArtUri != oldArtUri) {
            updateDominantColor(newArtUri)
        }

        saveSession()
    }

    private val lyricsDir by lazy {
        File(getApplication<Application>().filesDir, "lyrics").apply { mkdirs() }
    }

    private fun getLyricsFile(title: String, artist: String): File {
        val safe = "${title.trim().lowercase()}_${artist.trim().lowercase()}".replace(Regex("[^a-z0-9_]"), "_")
        return File(lyricsDir, "$safe.lrc")
    }

    fun getCurrentAudioFile(): AudioFile? {
        val currentMediaId = _playbackState.value.mediaId
        return originalTracks.find { it.contentUri.toString() == currentMediaId }
    }

    private fun fetchLyrics(title: String, artist: String) {
        val cacheKey = "$title|$artist"
        lyricsCache[cacheKey]?.let { (plain, synced) ->
            _playbackState.update {
                it.copy(plainLyrics = plain, syncedLyrics = synced, isLyricsLoading = false)
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _playbackState.update {
                it.copy(
                    isLyricsLoading = true,
                    plainLyrics = null,
                    syncedLyrics = null
                )
            }

            // 1. Check local embedded/saved lyrics file first
            val localFile = getLyricsFile(title, artist)
            if (localFile.exists()) {
                val content = localFile.readText()
                val synced = LyricsParser.parse(content)
                val plain = if (synced.isEmpty()) content else null
                val finalSynced = if (synced.isEmpty()) null else synced

                lyricsCache[cacheKey] = plain to finalSynced
                _playbackState.update {
                    it.copy(
                        plainLyrics = plain,
                        syncedLyrics = finalSynced,
                        isLyricsLoading = false
                    )
                }
                return@launch
            }

            // Also check companion .lrc beside audio file
            val currentAudio = getCurrentAudioFile()
            if (currentAudio != null && currentAudio.filePath.isNotBlank()) {
                val companionFile = File(currentAudio.filePath.substringBeforeLast('.') + ".lrc")
                if (companionFile.exists()) {
                    val content = companionFile.readText()
                    val synced = LyricsParser.parse(content)
                    val plain = if (synced.isEmpty()) content else null
                    val finalSynced = if (synced.isEmpty()) null else synced

                    lyricsCache[cacheKey] = plain to finalSynced
                    _playbackState.update {
                        it.copy(
                            plainLyrics = plain,
                            syncedLyrics = finalSynced,
                            isLyricsLoading = false
                        )
                    }
                    return@launch
                }
            }

            // 2. Query online lyrics API if not stored locally
            try {
                val response = LyricsProvider.api.getLyrics(title, artist, null, null)
                val synced = response.syncedLyrics?.let { LyricsParser.parse(it) }
                val plain = response.plainLyrics

                // Embed/save to local storage if setting is enabled
                if (settingsManager.autoEmbedLyrics.value) {
                    if (!response.syncedLyrics.isNullOrBlank()) {
                        localFile.writeText(response.syncedLyrics)
                    } else if (!plain.isNullOrBlank()) {
                        localFile.writeText(plain)
                    }
                }

                lyricsCache[cacheKey] = plain to synced

                _playbackState.update {
                    it.copy(
                        plainLyrics = plain,
                        syncedLyrics = if (synced.isNullOrEmpty()) null else synced,
                        isLyricsLoading = false
                    )
                }
            } catch (e: Exception) {
                _playbackState.update { it.copy(isLyricsLoading = false) }
            }
        }
    }

    fun rescanLibrary() {
        loadTracks()
    }

    fun saveCustomLyrics(track: AudioFile, lrcContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (settingsManager.autoEmbedLyrics.value) {
                    val localFile = getLyricsFile(track.title, track.artist)
                    localFile.writeText(lrcContent)

                    if (track.filePath.isNotBlank()) {
                        try {
                            val companionFile = File(track.filePath.substringBeforeLast('.') + ".lrc")
                            companionFile.writeText(lrcContent)
                        } catch (e: Exception) {
                            // Scoped storage fallback
                        }
                    }
                }

                val synced = LyricsParser.parse(lrcContent)
                val plain = if (synced.isEmpty()) lrcContent else null
                val finalSynced = if (synced.isEmpty()) null else synced

                val cacheKey = "${track.title}|${track.artist}"
                lyricsCache[cacheKey] = plain to finalSynced

                if (_playbackState.value.mediaId == track.contentUri.toString()) {
                    _playbackState.update {
                        it.copy(
                            plainLyrics = plain,
                            syncedLyrics = finalSynced,
                            isLyricsLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var lastExtractedArtUri: String? = null

    private fun updateDominantColor(uriString: String?) {
        if (uriString == null) {
            lastExtractedArtUri = null
            _playbackState.update { it.copy(dominantColor = 0xFF1C1B1F.toInt()) }
            return
        }
        if (uriString == lastExtractedArtUri) return
        lastExtractedArtUri = uriString

        viewModelScope.launch {
            val context = getApplication<Application>()
            val request = ImageRequest.Builder(context)
                .data(uriString.toUri())
                .allowHardware(false) // Required for Palette to work with Bitmaps
                .size(Size(128, 128)) // Downscale image to save memory for palette extraction
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap).generate()
                    }
                    val color = palette.getVibrantColor(
                        palette.getMutedColor(
                            palette.getDominantColor(0xFF1C1B1F.toInt())
                        )
                    )
                    _playbackState.update { it.copy(dominantColor = color) }
                }
            }
        }
    }

    private fun saveSession() {
        val now = System.currentTimeMillis()
        if (now - lastSaveTimeMs < 5000L) return  // Throttle to once per 5 seconds
        lastSaveTimeMs = now
        
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val uri = currentItem.mediaId.ifEmpty { currentItem.localConfiguration?.uri?.toString() }
        val pos = controller.currentPosition
        val shuffle = _playbackState.value.shuffleModeEnabled
        val repeat = controller.repeatMode

        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit {
                putString("last_track_uri", uri)
                putLong("last_position", pos)
                putBoolean("shuffle_mode", shuffle)
                putInt("repeat_mode", repeat)
            }
        }
    }

    // ── Actions from UI ────────────────────────────────────────────────
    fun playPause() {
        mediaController?.let { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _progressState.update { it.copy(positionMs = positionMs) }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun setCustomPlaylistCoverArt(playlistId: Long, uri: Uri) {
        _libraryState.update { state ->
            val updatedPlaylists = state.playlists.map { p ->
                if (p.id == playlistId) p.copy(customCoverArtUri = uri) else p
            }
            val updatedSelected = if (state.selectedPlaylist?.id == playlistId) {
                state.selectedPlaylist.copy(customCoverArtUri = uri)
            } else state.selectedPlaylist
            state.copy(playlists = updatedPlaylists, selectedPlaylist = updatedSelected)
        }
    }

    fun playPlaylist(tracks: List<AudioFile>, startIndex: Int = 0, shuffle: Boolean = false, openNowPlaying: Boolean = true) {
        if (tracks.isEmpty()) return
        mediaController?.let { ctrl ->
            val listToPlay = if (shuffle) tracks.shuffled() else tracks
            val mediaItems = listToPlay.toMediaItems()
            val actualStartIndex = if (shuffle) 0 else startIndex.coerceIn(0, mediaItems.size - 1)
            ctrl.setMediaItems(mediaItems, actualStartIndex, 0L)
            ctrl.prepare()
            ctrl.play()
        }
        if (openNowPlaying) {
            openNowPlaying()
        }
    }

    fun playTrack(track: AudioFile, playlistTracks: List<AudioFile> = emptyList(), openNowPlaying: Boolean = true) {
        if (playlistTracks.isNotEmpty()) {
            val index = playlistTracks.indexOfFirst { it.contentUri == track.contentUri }
            playPlaylist(playlistTracks, startIndex = if (index != -1) index else 0, shuffle = false, openNowPlaying = openNowPlaying)
        } else {
            mediaController?.let { ctrl ->
                val trackUri = track.contentUri.toString()
                var index = -1
                
                // Find the index in the current media controller queue
                for (i in 0 until ctrl.mediaItemCount) {
                    val item = ctrl.getMediaItemAt(i)
                    if (item.mediaId == trackUri || item.localConfiguration?.uri?.toString() == trackUri) {
                        index = i
                        break
                    }
                }
                
                if (index != -1) {
                    ctrl.seekTo(index, 0L)
                    ctrl.play()
                }
            }
            if (openNowPlaying) {
                openNowPlaying()
            }
        }
    }

    fun openNowPlaying() {
        _playbackState.update { it.copy(isNowPlayingOpen = true) }
    }

    fun closeNowPlaying() {
        _playbackState.update { it.copy(isNowPlayingOpen = false) }
    }

    fun switchTab(index: Int) {
        _playbackState.update { it.copy(currentTab = index) }
    }

    // ── Library Actions ────────────────────────────────────────────────
    fun setSearchQuery(query: String) {
        _libraryState.update { it.copy(searchQuery = query) }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(PlaylistEntity(id = playlist.id, name = playlist.name))
            if (_libraryState.value.selectedPlaylist?.id == playlist.id) {
                _libraryState.update { it.copy(selectedPlaylist = null) }
            }
        }
    }

    fun switchLibraryTab(tab: LibraryTab) {
        _libraryState.update { it.copy(activeTab = tab, selectedPlaylist = null) }
    }

    fun toggleLibraryLayout() {
        _libraryState.update {
            it.copy(layout = if (it.layout == LibraryLayout.List) LibraryLayout.Tiles else LibraryLayout.List)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _libraryState.update { state ->
            val updated = state.copy(sortOrder = order)
            applySorting(updated)
        }
    }

    fun toggleSortDirection() {
        _libraryState.update { state ->
            val newDir = if (state.sortDirection == SortDirection.Ascending) SortDirection.Descending else SortDirection.Ascending
            val updated = state.copy(sortDirection = newDir)
            applySorting(updated)
        }
    }

    private fun applySorting(state: LibraryState): LibraryState {
        val sortedAll = sortTrackList(originalTracks, state.sortOrder, state.sortDirection)
        val sortedSelected = state.selectedPlaylist?.let { pl ->
            pl.copy(tracks = sortTrackList(pl.tracks, state.sortOrder, state.sortDirection))
        }
        return state.copy(allSongs = sortedAll, selectedPlaylist = sortedSelected)
    }

    fun sortTrackList(tracks: List<AudioFile>, order: SortOrder, direction: SortDirection): List<AudioFile> {
        val sorted = when (order) {
            SortOrder.Title -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOrder.Artist -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            SortOrder.Album -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
            SortOrder.Duration -> tracks.sortedBy { it.duration }
            SortOrder.DateAdded -> tracks.sortedBy { it.dateAdded }
            SortOrder.Year -> tracks.sortedBy { it.year }
        }
        return if (direction == SortDirection.Descending) sorted.reversed() else sorted
    }

    fun selectPlaylist(playlist: Playlist?) {
        _libraryState.update { state ->
            if (playlist != null) {
                val sortedTracks = sortTrackList(playlist.tracks, state.sortOrder, state.sortDirection)
                state.copy(selectedPlaylist = playlist.copy(tracks = sortedTracks))
            } else {
                state.copy(selectedPlaylist = null)
            }
        }
    }

    fun navigateToArtist(artistName: String) {
        val artistTracks = originalTracks.filter { it.artist.equals(artistName, ignoreCase = true) }
        if (artistTracks.isNotEmpty()) {
            _libraryState.update { state ->
                val sorted = sortTrackList(artistTracks, state.sortOrder, state.sortDirection)
                state.copy(
                    activeTab = LibraryTab.Artists,
                    selectedPlaylist = Playlist(name = artistName, tracks = sorted)
                )
            }
        }
    }

    fun navigateToAlbum(albumName: String) {
        val albumTracks = originalTracks.filter { it.album.equals(albumName, ignoreCase = true) }
        if (albumTracks.isNotEmpty()) {
            _libraryState.update { state ->
                val sorted = sortTrackList(albumTracks, state.sortOrder, state.sortDirection)
                state.copy(
                    activeTab = LibraryTab.Albums,
                    selectedPlaylist = Playlist(name = albumName, tracks = sorted)
                )
            }
        }
    }

    fun setSearchCategory(category: String) {
        _libraryState.update { it.copy(searchCategory = category) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistDao.insertPlaylist(PlaylistEntity(name = name))
            // observePlaylists will pick up the change
        }
    }

    fun toggleSelection(track: AudioFile) {
        val current = _libraryState.value.selectedTracks
        val updated = if (current.contains(track)) {
            current - track
        } else {
            current + track
        }
        _libraryState.update { it.copy(selectedTracks = updated) }
    }

    fun selectAll(tracks: List<AudioFile>) {
        _libraryState.update { it.copy(selectedTracks = tracks.toSet()) }
    }

    fun deselectAll() {
        _libraryState.update { it.copy(selectedTracks = emptySet()) }
    }

    fun clearSelection() {
        _libraryState.update { it.copy(selectedTracks = emptySet()) }
    }

    fun addSelectedToPlaylist(playlistName: String) {
        val selected = _libraryState.value.selectedTracks
        if (selected.isEmpty()) return
        addTracksToPlaylist(playlistName, selected.toList())
        clearSelection()
    }

    fun addTrackToPlaylist(playlistName: String, track: AudioFile) {
        addTracksToPlaylist(playlistName, listOf(track))
    }

    fun addTracksToPlaylist(playlistName: String, tracks: List<AudioFile>) {
        if (tracks.isEmpty()) return
        val playlist = _libraryState.value.playlists.find { it.name == playlistName } ?: return
        
        viewModelScope.launch {
            tracks.forEach { track ->
                playlistDao.addSongToPlaylist(
                    PlaylistSongEntity(playlistId = playlist.id, trackUri = track.contentUri.toString())
                )
            }
            populatePlaylistsWithTracks()
        }
    }

    fun createPlaylistWithSelected(name: String) {
        val selected = _libraryState.value.selectedTracks.toList()
        if (selected.isEmpty()) return
        createPlaylistWithTracks(name, selected)
        clearSelection()
    }

    fun createPlaylistWithTracks(name: String, tracks: List<AudioFile>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistDao.insertPlaylist(PlaylistEntity(name = name))
            tracks.forEach { track ->
                playlistDao.addSongToPlaylist(
                    PlaylistSongEntity(playlistId = playlistId, trackUri = track.contentUri.toString())
                )
            }
            populatePlaylistsWithTracks()
        }
    }

    fun removeSelectedFromPlaylist() {
        val selected = _libraryState.value.selectedTracks
        val currentPlaylist = _libraryState.value.selectedPlaylist ?: return
        if (selected.isEmpty() || currentPlaylist.name == "Device Files") return

        viewModelScope.launch {
            selected.forEach { track ->
                playlistDao.removeSongFromPlaylist(currentPlaylist.id, track.contentUri.toString())
            }
            clearSelection()
            populatePlaylistsWithTracks()
            val updatedPlaylist = _libraryState.value.playlists.find { it.id == currentPlaylist.id }
            _libraryState.update { it.copy(selectedPlaylist = updatedPlaylist) }
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let { ctrl ->
            val nextMode = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            ctrl.repeatMode = nextMode
        }
    }

    fun seekToQueueIndex(index: Int) {
        mediaController?.let { ctrl ->
            if (index in 0 until ctrl.mediaItemCount) {
                ctrl.seekTo(index, 0L)
                ctrl.play()
            }
        }
    }

    fun toggleShuffleMode() {
        mediaController?.let { ctrl ->
            val newShuffle = !ctrl.shuffleModeEnabled
            ctrl.shuffleModeEnabled = newShuffle
            _playbackState.update { it.copy(shuffleModeEnabled = newShuffle) }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        mediaController?.let { ctrl ->
            if (fromIndex in 0 until ctrl.mediaItemCount && toIndex in 0 until ctrl.mediaItemCount) {
                ctrl.moveMediaItem(fromIndex, toIndex)
                syncState(ctrl)
            }
        }
    }

    fun removeTrack(index: Int) {
        mediaController?.let { ctrl ->
            if (index in 0 until ctrl.mediaItemCount) {
                ctrl.removeMediaItem(index)
                syncState(ctrl)
            }
        }
    }

    fun playNext(track: AudioFile) {
        mediaController?.let { ctrl ->
            val nextIndex = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
            ctrl.addMediaItem(nextIndex, track.toMediaItem())
            syncState(ctrl)
        }
    }

    fun playNextBatch(tracks: List<AudioFile>) {
        if (tracks.isEmpty()) return
        mediaController?.let { ctrl ->
            val nextIndex = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
            ctrl.addMediaItems(nextIndex, tracks.toMediaItems())
            syncState(ctrl)
        }
    }

    fun addTracksToQueue(tracks: List<AudioFile>) {
        if (tracks.isEmpty()) return
        mediaController?.let { ctrl ->
            ctrl.addMediaItems(tracks.toMediaItems())
            syncState(ctrl)
        }
    }

    fun playAll(tracks: List<AudioFile>, shuffle: Boolean = false, openNowPlaying: Boolean = true) {
        if (tracks.isEmpty()) return
        playPlaylist(tracks, shuffle = shuffle, openNowPlaying = openNowPlaying)
    }

    fun addToQueue(track: AudioFile) {
        mediaController?.let { ctrl ->
            ctrl.addMediaItem(track.toMediaItem())
            syncState(ctrl)
        }
    }

    fun updateTrackMetadata(
        track: AudioFile,
        title: String,
        artist: String,
        album: String,
        genre: String = "",
        year: Int = 0,
        trackNumber: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = mediaStoreDataSource.updateMetadata(track, title, artist, album, genre, year, trackNumber)
            when (result) {
                is MetadataUpdateResult.Success -> {
                    // Refresh library to reflect changes
                    withContext(Dispatchers.Main) {
                        loadTracks()
                    }
                }
                is MetadataUpdateResult.PermissionRequired -> {
                    pendingUpdate = PendingUpdate(track, title, artist, album, genre, year, trackNumber)
                    _playbackState.update {
                        it.copy(pendingMetadataUpdate = result.pendingIntent)
                    }
                }
                is MetadataUpdateResult.Failure -> {
                    // Handle failure if needed
                }
            }
        }
    }

    fun consumePendingMetadataUpdate() {
        _playbackState.update { it.copy(pendingMetadataUpdate = null) }
    }

    fun retryPendingUpdate() {
        val update = pendingUpdate ?: return
        pendingUpdate = null
        updateTrackMetadata(update.track, update.title, update.artist, update.album, update.genre, update.year, update.trackNumber)
    }

    fun toggleFavorite(track: AudioFile) {
        viewModelScope.launch {
            val favoritesPlaylist = _libraryState.value.playlists.find { it.name == "Favorites" }
                ?: return@launch
            
            val isFav = favoritesPlaylist.tracks.any { it.contentUri.toString() == track.contentUri.toString() }
            
            if (isFav) {
                playlistDao.removeSongFromPlaylist(favoritesPlaylist.id, track.contentUri.toString())
            } else {
                playlistDao.addSongToPlaylist(
                    PlaylistSongEntity(playlistId = favoritesPlaylist.id, trackUri = track.contentUri.toString())
                )
            }
            populatePlaylistsWithTracks()
            
            // Update current playback state if the toggled track is the one playing
            if (_playbackState.value.mediaId == track.contentUri.toString()) {
                _playbackState.update { it.copy(isFavorite = !isFav) }
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        val bundle = android.os.Bundle().apply {
            putInt(EXTRA_SLEEP_MINUTES, minutes)
        }
        mediaController?.sendCustomCommand(
            SessionCommand(ACTION_SET_SLEEP_TIMER, android.os.Bundle.EMPTY),
            bundle
        )
    }

    fun cancelSleepTimer() {
        mediaController?.sendCustomCommand(
            SessionCommand(ACTION_CANCEL_SLEEP_TIMER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
    }

    override fun onCleared() {
        saveSession()
        handler.removeCallbacks(positionUpdateRunnable)
        mediaController?.release()
        mediaController = null
    }
}
