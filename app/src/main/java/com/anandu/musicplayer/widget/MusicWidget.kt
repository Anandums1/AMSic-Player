package com.anandu.musicplayer.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.anandu.musicplayer.R
import com.anandu.musicplayer.player.PlaybackService
import com.anandu.musicplayer.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
class MusicWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_BAR = DpSize(180.dp, 60.dp)
        private val MEDIUM_CARD = DpSize(260.dp, 120.dp)
        private val LARGE_HERO = DpSize(260.dp, 200.dp)

        private fun colorProvider(color: Color) =
            androidx.glance.color.ColorProvider(day = color, night = color)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL_BAR, MEDIUM_CARD, LARGE_HERO)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Pre-load downscaled album art from cache with zero UI-thread lag
        val artBitmap: Bitmap? = withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "widget_art.png")
                if (cacheFile.exists()) {
                    BitmapFactory.decodeFile(cacheFile.absolutePath)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val size = LocalSize.current

            val hasTrack = prefs[WidgetKeys.HAS_TRACK] ?: false
            val title = prefs[WidgetKeys.TITLE] ?: "No track playing"
            val artist = prefs[WidgetKeys.ARTIST] ?: ""
            val album = prefs[WidgetKeys.ALBUM] ?: ""
            val isPlaying = prefs[WidgetKeys.IS_PLAYING] ?: false
            val isFavorite = prefs[WidgetKeys.IS_FAVORITE] ?: false
            val shuffleMode = prefs[WidgetKeys.SHUFFLE_MODE] ?: false
            val repeatMode = prefs[WidgetKeys.REPEAT_MODE] ?: Player.REPEAT_MODE_OFF
            val durationMs = prefs[WidgetKeys.DURATION_MS] ?: 0L
            val positionMs = prefs[WidgetKeys.POSITION_MS] ?: 0L
            val dominantColorInt = prefs[WidgetKeys.DOMINANT_COLOR] ?: 0xFF1E1E28.toInt()
            val backgroundColor = Color(dominantColorInt)

            if (!hasTrack && title == "No track playing") {
                EmptyWidgetContent(context)
            } else {
                when {
                    size.height < 100.dp -> {
                        CompactWidgetContent(
                            context = context,
                            title = title,
                            artist = artist,
                            isPlaying = isPlaying,
                            albumArt = artBitmap,
                            backgroundColor = backgroundColor
                        )
                    }
                    size.height < 185.dp -> {
                        StandardWidgetContent(
                            context = context,
                            title = title,
                            artist = artist,
                            album = album,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            shuffleMode = shuffleMode,
                            durationMs = durationMs,
                            positionMs = positionMs,
                            albumArt = artBitmap,
                            backgroundColor = backgroundColor
                        )
                    }
                    else -> {
                        ExpandedWidgetContent(
                            context = context,
                            title = title,
                            artist = artist,
                            album = album,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            shuffleMode = shuffleMode,
                            repeatMode = repeatMode,
                            durationMs = durationMs,
                            positionMs = positionMs,
                            albumArt = artBitmap,
                            backgroundColor = backgroundColor
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1. Compact Horizontal Bar (e.g. 4x1, 3x1, 2x1)
    // ─────────────────────────────────────────────────────────────────────
    @Composable
    private fun CompactWidgetContent(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap?,
        backgroundColor: Color
    ) {
        val openAppAction = actionOpenApp(context)

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(18.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Thumbnail
            if (albumArt != null) {
                Image(
                    provider = ImageProvider(albumArt),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .size(44.dp)
                        .cornerRadius(10.dp)
                        .clickable(openAppAction)
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(44.dp)
                        .cornerRadius(10.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(openAppAction),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_music_note),
                        contentDescription = "Music Note",
                        modifier = GlanceModifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Track Details (Title & Artist)
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(openAppAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = colorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = artist.ifEmpty { "AMSic Player" },
                    style = TextStyle(
                        color = colorProvider(Color(0xFFC4C0CC)),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Controls (Previous, Play/Pause, Next)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    modifier = GlanceModifier
                        .size(32.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PREV))
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                    modifier = GlanceModifier
                        .size(38.dp)
                        .cornerRadius(19.dp)
                        .background(Color(0xFF7B61FF))
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier.size(22.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Image(
                    provider = ImageProvider(R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    modifier = GlanceModifier
                        .size(32.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_NEXT))
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. Standard Media Card (e.g. 4x2, 3x2, 5x2)
    // ─────────────────────────────────────────────────────────────────────
    @Composable
    private fun StandardWidgetContent(
        context: Context,
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        isFavorite: Boolean,
        shuffleMode: Boolean,
        durationMs: Long,
        positionMs: Long,
        albumArt: Bitmap?,
        backgroundColor: Color
    ) {
        val openAppAction = actionOpenApp(context)
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(22.dp)
                .padding(12.dp)
        ) {
            // Upper row: Artwork + Details
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                if (albumArt != null) {
                    Image(
                        provider = ImageProvider(albumArt),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .size(72.dp)
                            .cornerRadius(14.dp)
                            .clickable(openAppAction)
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(72.dp)
                            .cornerRadius(14.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(openAppAction),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_music_note),
                            contentDescription = "Music Note",
                            modifier = GlanceModifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(12.dp))

                // Track Info & Progress
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(openAppAction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            color = colorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = artist.ifEmpty { "AMSic Player" },
                        style = TextStyle(
                            color = colorProvider(Color(0xFFD0CCE0)),
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                    if (album.isNotEmpty()) {
                        Text(
                            text = album,
                            style = TextStyle(
                                color = colorProvider(Color(0xFF9E9EAA)),
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }

                    if (durationMs > 0) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Lower row: Control Deck (Favorite, Prev, Play/Pause, Next, Shuffle)
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Favorite Heart
                Image(
                    provider = ImageProvider(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    contentDescription = if (isFavorite) "Favorited" else "Favorite",
                    modifier = GlanceModifier
                        .size(34.dp)
                        .padding(5.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_FAVORITE))
                )

                Spacer(modifier = GlanceModifier.width(16.dp))

                // Previous
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    modifier = GlanceModifier
                        .size(36.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PREV))
                )

                Spacer(modifier = GlanceModifier.width(16.dp))

                // Play / Pause Circle
                Box(
                    modifier = GlanceModifier
                        .size(46.dp)
                        .cornerRadius(23.dp)
                        .background(Color(0xFF7B61FF))
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier.size(26.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(16.dp))

                // Next
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    modifier = GlanceModifier
                        .size(36.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_NEXT))
                )

                Spacer(modifier = GlanceModifier.width(16.dp))

                // Shuffle
                Image(
                    provider = ImageProvider(R.drawable.ic_shuffle),
                    contentDescription = "Shuffle",
                    modifier = GlanceModifier
                        .size(34.dp)
                        .cornerRadius(17.dp)
                        .background(if (shuffleMode) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(5.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_SHUFFLE))
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. Expanded Showcase Hero (e.g. 4x3, 4x4, 5x3)
    // ─────────────────────────────────────────────────────────────────────
    @Composable
    private fun ExpandedWidgetContent(
        context: Context,
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        isFavorite: Boolean,
        shuffleMode: Boolean,
        repeatMode: Int,
        durationMs: Long,
        positionMs: Long,
        albumArt: Bitmap?,
        backgroundColor: Color
    ) {
        val openAppAction = actionOpenApp(context)
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(24.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Header: App Tag + Favorite
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A MSic Player",
                    style = TextStyle(
                        color = colorProvider(Color(0xFFCFBCFF)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Image(
                    provider = ImageProvider(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    contentDescription = "Favorite",
                    modifier = GlanceModifier
                        .size(32.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_FAVORITE))
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Center Hero: Album Art + Titles
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(openAppAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (albumArt != null) {
                    Image(
                        provider = ImageProvider(albumArt),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier
                            .size(96.dp)
                            .cornerRadius(18.dp)
                    )
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(96.dp)
                            .cornerRadius(18.dp)
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_music_note),
                            contentDescription = "Music Note",
                            modifier = GlanceModifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.width(14.dp))

                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            color = colorProvider(Color.White),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        maxLines = 2
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = artist.ifEmpty { "Unknown Artist" },
                        style = TextStyle(
                            color = colorProvider(Color(0xFFD0CCE0)),
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                    if (album.isNotEmpty()) {
                        Text(
                            text = album,
                            style = TextStyle(
                                color = colorProvider(Color(0xFF9E9EAA)),
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Progress Bar with Timestamps
            if (durationMs > 0) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp)
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = TextStyle(
                            color = colorProvider(Color(0xFF9E9EAA)),
                            fontSize = 10.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        text = formatTime(durationMs),
                        style = TextStyle(
                            color = colorProvider(Color(0xFF9E9EAA)),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Full 5-Control Deck
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Shuffle Toggle
                Image(
                    provider = ImageProvider(R.drawable.ic_shuffle),
                    contentDescription = "Shuffle",
                    modifier = GlanceModifier
                        .size(36.dp)
                        .cornerRadius(18.dp)
                        .background(if (shuffleMode) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(6.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_SHUFFLE))
                )

                Spacer(modifier = GlanceModifier.width(18.dp))

                // Previous
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_previous),
                    contentDescription = "Previous",
                    modifier = GlanceModifier
                        .size(40.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PREV))
                )

                Spacer(modifier = GlanceModifier.width(18.dp))

                // Hero Play/Pause Button
                Box(
                    modifier = GlanceModifier
                        .size(52.dp)
                        .cornerRadius(26.dp)
                        .background(Color(0xFF7B61FF))
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier.size(30.dp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(18.dp))

                // Next
                Image(
                    provider = ImageProvider(R.drawable.ic_skip_next),
                    contentDescription = "Next",
                    modifier = GlanceModifier
                        .size(40.dp)
                        .padding(4.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_NEXT))
                )

                Spacer(modifier = GlanceModifier.width(18.dp))

                // Repeat Toggle
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    else -> R.drawable.ic_repeat
                }
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF

                Image(
                    provider = ImageProvider(repeatIcon),
                    contentDescription = "Repeat",
                    modifier = GlanceModifier
                        .size(36.dp)
                        .cornerRadius(18.dp)
                        .background(if (isRepeatActive) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(6.dp)
                        .clickable(actionService(context, PlaybackService.ACTION_WIDGET_REPEAT))
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. Smart Empty/Idle State
    // ─────────────────────────────────────────────────────────────────────
    @Composable
    private fun EmptyWidgetContent(context: Context) {
        val openAppAction = actionOpenApp(context)

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1E1E28))
                .cornerRadius(22.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(54.dp)
                    .cornerRadius(14.dp)
                    .background(Color(0xFF3D325E))
                    .clickable(openAppAction),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_music_note),
                    contentDescription = "A MSic Player",
                    modifier = GlanceModifier.size(30.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(14.dp))

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(openAppAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A MSic Player",
                    style = TextStyle(
                        color = colorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = "Your library is ready",
                    style = TextStyle(
                        color = colorProvider(Color(0xFFC4C0CC)),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Play Library Button
            Box(
                modifier = GlanceModifier
                    .cornerRadius(16.dp)
                    .background(Color(0xFF7B61FF))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable(actionService(context, PlaybackService.ACTION_WIDGET_PLAY_LIBRARY)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Play",
                    style = TextStyle(
                        color = colorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }

    private fun actionService(context: Context, actionName: String): Action {
        return actionStartService(
            Intent(context, PlaybackService::class.java).apply { action = actionName },
            isForegroundService = false
        )
    }

    private fun actionOpenApp(context: Context): Action {
        return actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("open_now_playing", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
