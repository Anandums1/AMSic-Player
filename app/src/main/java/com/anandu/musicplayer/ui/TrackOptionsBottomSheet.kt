package com.anandu.musicplayer.ui

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.anandu.musicplayer.data.AudioFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: AudioFile,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onEditMetadata: () -> Unit,
    onShowTrackInfo: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissWith(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Track preview & audio specs badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (track.albumArtUri != null) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    // Format & Bitrate Pill
                    val qualityLabel = buildString {
                        if (track.mimeType.contains("flac", ignoreCase = true)) {
                            append("FLAC")
                        } else if (track.mimeType.contains("mp4", ignoreCase = true) || track.mimeType.contains("m4a", ignoreCase = true)) {
                            append("AAC")
                        } else if (track.bitrateBps > 0) {
                            append("${track.bitrateBps / 1000} kbps")
                        } else {
                            append("Audio")
                        }
                        if (track.year > 0) append(" • ${track.year}")
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Playback Options
            SheetActionItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Play Next",
                subtitle = "Insert directly after current song",
                onClick = { dismissWith { onPlayNext() } }
            )

            SheetActionItem(
                icon = Icons.Rounded.Queue,
                title = "Add to Queue",
                subtitle = "Append to the end of the queue",
                onClick = { dismissWith { onAddToQueue() } }
            )

            SheetActionItem(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                title = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                iconTint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { dismissWith { onToggleFavorite() } }
            )

            SheetActionItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                title = "Add to Playlist...",
                onClick = { dismissWith { onAddToPlaylist() } }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Navigation
            SheetActionItem(
                icon = Icons.Rounded.Person,
                title = "Go to Artist",
                subtitle = track.artist,
                onClick = { dismissWith { onGoToArtist() } }
            )

            SheetActionItem(
                icon = Icons.Rounded.Album,
                title = "Go to Album",
                subtitle = track.album,
                onClick = { dismissWith { onGoToAlbum() } }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Details & Tools
            SheetActionItem(
                icon = Icons.Rounded.Info,
                title = "Track Details & Audio Info",
                subtitle = "Bitrate, sample rate, path, size",
                onClick = { dismissWith { onShowTrackInfo() } }
            )

            SheetActionItem(
                icon = Icons.Rounded.Edit,
                title = "Edit Metadata",
                subtitle = "Title, artist, album, genre, year",
                onClick = { dismissWith { onEditMetadata() } }
            )

            SheetActionItem(
                icon = Icons.Rounded.Notifications,
                title = "Set as Ringtone",
                onClick = { dismissWith { setAsRingtone(context, track) } }
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun setAsRingtone(context: Context, track: AudioFile) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please grant permission to modify system settings", Toast.LENGTH_LONG).show()
            return
        }
    }

    try {
        RingtoneManager.setActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_RINGTONE,
            track.contentUri
        )
        Toast.makeText(context, "Set as default ringtone: ${track.title}", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to set ringtone: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
