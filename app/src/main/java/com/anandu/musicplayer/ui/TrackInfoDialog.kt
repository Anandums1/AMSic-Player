package com.anandu.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anandu.musicplayer.data.AudioFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackInfoDialog(
    track: AudioFile,
    onDismiss: () -> Unit
) {
    val sizeMb = if (track.sizeBytes > 0) "%.2f MB".format(track.sizeBytes / (1024.0 * 1024.0)) else "Unknown"
    val bitrateKbps = if (track.bitrateBps > 0) "${track.bitrateBps / 1000} kbps" else "Unknown"
    val sampleRate = if (track.sampleRateHz > 0) "${track.sampleRateHz} Hz" else "Standard (44.1 kHz)"
    val formatText = if (track.mimeType.isNotBlank()) track.mimeType else "Audio"
    val pathText = if (track.filePath.isNotBlank()) track.filePath else track.contentUri.toString()

    val dateAddedStr = if (track.dateAdded > 0) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(track.dateAdded * 1000L))
    } else {
        "Unknown"
    }

    val durationMinSec = formatDuration(track.duration)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Track Information",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoItem(label = "Title", value = track.title)
                InfoItem(label = "Artist", value = track.artist)
                InfoItem(label = "Album", value = track.album)
                if (track.genre.isNotBlank()) InfoItem(label = "Genre", value = track.genre)
                if (track.year > 0) InfoItem(label = "Year", value = track.year.toString())
                if (track.trackNumber.isNotBlank()) InfoItem(label = "Track No.", value = track.trackNumber)
                InfoItem(label = "Duration", value = durationMinSec)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Technical Specifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InfoItem(label = "Format", value = formatText)
                        InfoItem(label = "Bitrate", value = bitrateKbps)
                        InfoItem(label = "Sample Rate", value = sampleRate)
                        InfoItem(label = "File Size", value = sizeMb)
                        InfoItem(label = "Date Added", value = dateAddedStr)
                        InfoItem(label = "File Path", value = pathText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
