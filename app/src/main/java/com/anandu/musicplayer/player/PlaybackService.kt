package com.anandu.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.anandu.musicplayer.R
import com.anandu.musicplayer.data.MediaStoreDataSource
import com.anandu.musicplayer.data.db.PlaylistDao
import com.anandu.musicplayer.data.db.PlaylistEntity
import com.anandu.musicplayer.data.db.PlaylistSongEntity
import com.anandu.musicplayer.ui.MainActivity
import com.anandu.musicplayer.widget.MusicWidget
import com.anandu.musicplayer.widget.WidgetKeys
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Enhanced background playback service that wraps [ExoPlayer] in a [MediaSession]
 * and provides a customized notification using [NotificationCompat].
 */
@UnstableApi
class PlaybackService : MediaSessionService(), KoinComponent {

    private val playlistDao: PlaylistDao by inject()
    private val mediaStoreDataSource: MediaStoreDataSource by inject()

    private var mediaSession: MediaSession? = null

    companion object {
        const val CHANNEL_ID = "playback_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "com.anandu.musicplayer.STOP"
        const val ACTION_SET_SLEEP_TIMER = "com.anandu.musicplayer.SET_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.anandu.musicplayer.CANCEL_SLEEP_TIMER"
        const val EXTRA_SLEEP_MINUTES = "extra_sleep_minutes"
        const val EXTRA_REMAINING_MS = "extra_remaining_ms"
        
        const val ACTION_WIDGET_PLAY_PAUSE = "com.anandu.musicplayer.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.anandu.musicplayer.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.anandu.musicplayer.WIDGET_PREV"
        const val ACTION_WIDGET_FAVORITE = "com.anandu.musicplayer.WIDGET_FAVORITE"
        const val ACTION_WIDGET_SHUFFLE = "com.anandu.musicplayer.WIDGET_SHUFFLE"
        const val ACTION_WIDGET_REPEAT = "com.anandu.musicplayer.WIDGET_REPEAT"
        const val ACTION_WIDGET_PLAY_LIBRARY = "com.anandu.musicplayer.WIDGET_PLAY_LIBRARY"
    }

    private var sleepTimer: CountDownTimer? = null
    private var sleepTimerRemainingMs = 0L
    private var lastWidgetArtworkUri: String? = null
    private var lastWidgetDominantColor: Int = 0xFF1E1E28.toInt()

    private fun updateSessionExtras() {
        val extras = Bundle().apply {
            putLong(EXTRA_REMAINING_MS, sleepTimerRemainingMs)
        }
        mediaSession?.setSessionExtras(extras)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val callback = object : MediaSession.Callback {
            @OptIn(UnstableApi::class)
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                // Return connection result that allows our custom commands
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_GET_METADATA)
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()
                )
            }

            @OptIn(UnstableApi::class)
            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_STOP -> {
                        stopSelf()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_SET_SLEEP_TIMER -> {
                        val minutes = args.getInt(EXTRA_SLEEP_MINUTES, 0)
                        if (minutes > 0) {
                            startSleepTimer(minutes)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    ACTION_CANCEL_SLEEP_TIMER -> {
                        cancelSleepTimer()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_now_playing", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(pendingIntent)
            .build()

        // Set the custom notification provider
        setMediaNotificationProvider(CustomNotificationProvider())

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateWidgetState()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidgetState()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateWidgetState()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateWidgetState()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidgetState()
            }
        })
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val player = mediaSession?.player
        when (action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (player != null) {
                    if (player.mediaItemCount == 0) {
                        playLibraryTracks()
                    } else {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
            }
            ACTION_WIDGET_NEXT -> player?.seekToNextMediaItem()
            ACTION_WIDGET_PREV -> player?.seekToPreviousMediaItem()
            ACTION_WIDGET_FAVORITE -> toggleFavoriteFromWidget()
            ACTION_WIDGET_SHUFFLE -> {
                if (player != null) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    updateWidgetState()
                }
            }
            ACTION_WIDGET_REPEAT -> {
                if (player != null) {
                    val nextMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode = nextMode
                    updateWidgetState()
                }
            }
            ACTION_WIDGET_PLAY_LIBRARY -> playLibraryTracks()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun toggleFavoriteFromWidget() {
        val player = mediaSession?.player ?: return
        val currentItem = player.currentMediaItem ?: return
        val trackUri = currentItem.localConfiguration?.uri?.toString() ?: currentItem.mediaId
        if (trackUri.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlists = playlistDao.getAllPlaylists().first()
                val favoritesPlaylist = playlists.find { it.name == "Favorites" }
                    ?: run {
                        val id = playlistDao.insertPlaylist(PlaylistEntity(name = "Favorites"))
                        PlaylistEntity(id = id, name = "Favorites")
                    }
                val isFav = playlistDao.getAllPlaylistSongs()
                    .any { it.playlistId == favoritesPlaylist.id && it.trackUri == trackUri }
                if (isFav) {
                    playlistDao.removeSongFromPlaylist(favoritesPlaylist.id, trackUri)
                } else {
                    playlistDao.addSongToPlaylist(PlaylistSongEntity(playlistId = favoritesPlaylist.id, trackUri = trackUri))
                }
                updateWidgetState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playLibraryTracks() {
        val player = mediaSession?.player ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tracks = mediaStoreDataSource.queryAudioFiles()
                if (tracks.isNotEmpty()) {
                    val shuffled = tracks.shuffled()
                    val mediaItems = shuffled.map { track ->
                        androidx.media3.common.MediaItem.Builder()
                            .setMediaId(track.contentUri.toString())
                            .setUri(track.contentUri)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setAlbumTitle(track.album)
                                    .setArtworkUri(track.albumArtUri)
                                    .build()
                            )
                            .build()
                    }
                    withContext(Dispatchers.Main) {
                        player.setMediaItems(mediaItems, 0, 0L)
                        player.prepare()
                        player.play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateWidgetState() {
        val player = mediaSession?.player ?: return
        val metadata = player.mediaMetadata
        val currentItem = player.currentMediaItem
        val hasTrack = currentItem != null && player.mediaItemCount > 0
        val title = metadata.title?.toString() ?: if (hasTrack) "Unknown Title" else "No track playing"
        val artist = metadata.artist?.toString() ?: if (hasTrack) "Unknown Artist" else ""
        val album = metadata.albumTitle?.toString() ?: ""
        val isPlaying = player.isPlaying
        val artworkUri = metadata.artworkUri?.toString()
        val repeatMode = player.repeatMode
        val shuffleMode = player.shuffleModeEnabled
        val durationMs = player.duration.coerceAtLeast(0L)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val trackUri = currentItem?.localConfiguration?.uri?.toString() ?: currentItem?.mediaId ?: ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Determine favorite status
                var isFav = false
                if (trackUri.isNotEmpty()) {
                    try {
                        val favPlaylist = playlistDao.getAllPlaylists().first().find { it.name == "Favorites" }
                        if (favPlaylist != null) {
                            isFav = playlistDao.getAllPlaylistSongs().any { it.playlistId == favPlaylist.id && it.trackUri == trackUri }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Pre-extract dominant color and cache downscaled art thumbnail
                val cacheFile = File(cacheDir, "widget_art.png")
                var dominantColor = lastWidgetDominantColor
                if (artworkUri != null) {
                    if (artworkUri == lastWidgetArtworkUri && cacheFile.exists()) {
                        dominantColor = lastWidgetDominantColor
                    } else {
                        try {
                            val loader = ImageLoader(this@PlaybackService)
                            val request = ImageRequest.Builder(this@PlaybackService)
                                .data(artworkUri)
                                .size(160, 160)
                                .build()
                            val result = loader.execute(request)
                            if (result is SuccessResult) {
                                val bmp = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
                                FileOutputStream(cacheFile).use { out ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 85, out)
                                }
                                val palette = Palette.from(bmp).generate()
                                dominantColor = palette.getDarkMutedColor(
                                    palette.getDominantColor(0xFF1E1E28.toInt())
                                )
                                lastWidgetArtworkUri = artworkUri
                                lastWidgetDominantColor = dominantColor
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    lastWidgetArtworkUri = null
                    lastWidgetDominantColor = 0xFF1E1E28.toInt()
                    dominantColor = 0xFF1E1E28.toInt()
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                }

                val context = this@PlaybackService
                val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(MusicWidget::class.java)
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[WidgetKeys.HAS_TRACK] = hasTrack
                        prefs[WidgetKeys.TITLE] = title
                        prefs[WidgetKeys.ARTIST] = artist
                        prefs[WidgetKeys.ALBUM] = album
                        prefs[WidgetKeys.IS_PLAYING] = isPlaying
                        prefs[WidgetKeys.IS_FAVORITE] = isFav
                        prefs[WidgetKeys.SHUFFLE_MODE] = shuffleMode
                        prefs[WidgetKeys.REPEAT_MODE] = repeatMode
                        prefs[WidgetKeys.DURATION_MS] = durationMs
                        prefs[WidgetKeys.POSITION_MS] = positionMs
                        prefs[WidgetKeys.DOMINANT_COLOR] = dominantColor
                        if (artworkUri != null) {
                            prefs[WidgetKeys.ALBUM_ART_URI] = artworkUri
                        } else {
                            prefs.remove(WidgetKeys.ALBUM_ART_URI)
                        }
                    }
                }
                MusicWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startSleepTimer(minutes: Int) {
        val addMs = minutes * 60 * 1000L
        val totalMs = sleepTimerRemainingMs + addMs

        sleepTimer?.cancel()
        sleepTimerRemainingMs = totalMs
        updateSessionExtras()

        sleepTimer = object : CountDownTimer(totalMs, 1000L) { // Update every 1 second
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerRemainingMs = millisUntilFinished
                updateSessionExtras()
            }
            override fun onFinish() {
                sleepTimerRemainingMs = 0
                mediaSession?.player?.pause()
                updateSessionExtras()
            }
        }.start()
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerRemainingMs = 0
        updateSessionExtras()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music Playback",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    /**
     * Custom MediaNotification.Provider that uses ActionFactory correctly.
     */
    private inner class CustomNotificationProvider : MediaNotification.Provider {
        @OptIn(UnstableApi::class)
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            callback: MediaNotification.Provider.Callback
        ): MediaNotification {
            val player = mediaSession.player
            val metadata = player.mediaMetadata

            val builder = NotificationCompat.Builder(this@PlaybackService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play)
                .setContentTitle(metadata.title?.toString() ?: "Unknown Title")
                .setContentText(metadata.artist?.toString() ?: "Unknown Artist")
                .setContentIntent(mediaSession.sessionActivity)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(player.isPlaying)
                .setSilent(true)

            // Action 0: Skip Previous (Standard Media Action)
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(this@PlaybackService, android.R.drawable.ic_media_previous),
                    "Previous",
                    Player.COMMAND_SEEK_TO_PREVIOUS
                )
            )

            // Action 1: Play/Pause (Standard Media Action)
            val playPauseIcon = if (player.isPlaying) 
                android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(this@PlaybackService, playPauseIcon),
                    if (player.isPlaying) "Pause" else "Play",
                    Player.COMMAND_PLAY_PAUSE
                )
            )

            // Action 2: Skip Next (Standard Media Action)
            builder.addAction(
                actionFactory.createMediaAction(
                    mediaSession,
                    IconCompat.createWithResource(this@PlaybackService, android.R.drawable.ic_media_next),
                    "Next",
                    Player.COMMAND_SEEK_TO_NEXT
                )
            )

            // Action 3: Stop (Custom Session Action)
            builder.addAction(
                actionFactory.createCustomActionFromCustomCommandButton(
                    mediaSession,
                    CommandButton.Builder()
                        .setSessionCommand(SessionCommand(ACTION_STOP, Bundle.EMPTY))
                        .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
                        .setDisplayName("Stop")
                        .build()
                )
            )

            // Set MediaStyle after actions are added so action indices (0, 1, 2) are valid
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )

            // Asynchronously load artwork
            val artworkUri = metadata.artworkUri
            if (artworkUri != null) {
                val future = mediaSession.bitmapLoader.loadBitmap(artworkUri)
                Futures.addCallback(future, object : FutureCallback<Bitmap> {
                    override fun onSuccess(result: Bitmap?) {
                        if (result != null) {
                            builder.setLargeIcon(result)
                            callback.onNotificationChanged(MediaNotification(NOTIFICATION_ID, builder.build()))
                        }
                    }
                    override fun onFailure(t: Throwable) {}
                }, MoreExecutors.directExecutor())
            }

            return MediaNotification(NOTIFICATION_ID, builder.build())
        }

        @OptIn(UnstableApi::class)
        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false

        @OptIn(UnstableApi::class)
        override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
            return MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, "Music Playback")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
