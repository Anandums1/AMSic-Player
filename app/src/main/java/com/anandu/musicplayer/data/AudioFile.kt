package com.anandu.musicplayer.data

import android.net.Uri

import androidx.compose.runtime.Immutable

/**
 * Represents a single audio track from MediaStore.
 */
@Immutable
data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,        // milliseconds
    val contentUri: Uri,       // playable content:// URI
    val albumArtUri: Uri?,     // album art content:// URI (may be null)
    val year: Int = 0,
    val trackNumber: String = "",
    val genre: String = "",
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
    val filePath: String = "",
    val bitrateBps: Int = 0,
    val sampleRateHz: Int = 0,
    val dateAdded: Long = 0L,
)
