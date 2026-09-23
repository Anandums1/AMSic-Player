package com.anandu.musicplayer.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.provider.MediaStore

/**
 * Result of a metadata update operation.
 */
sealed class MetadataUpdateResult {
    object Success : MetadataUpdateResult()
    object Failure : MetadataUpdateResult()
    data class PermissionRequired(val pendingIntent: PendingIntent) : MetadataUpdateResult()
}

/**
 * Queries [MediaStore] for audio files on the device.
 */
class MediaStoreDataSource(private val context: Context) {

    private val albumArtBaseUri: Uri =
        "content://media/external/audio/albumart".toUri()

    /**
     * Returns all music tracks found on external storage, sorted by title.
     * Must be called after READ_MEDIA_AUDIO permission is granted.
     */
    fun queryAudioFiles(): List<AudioFile> {
        val audioFiles = mutableListOf<AudioFile>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val bitrateCol = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else 0L

                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Unknown" else "Unknown"
                    val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol != -1) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L

                    val year = if (yearCol != -1) cursor.getInt(yearCol) else 0
                    val trackNum = if (trackCol != -1) cursor.getString(trackCol) ?: "" else ""
                    val genre = if (genreCol != -1) cursor.getString(genreCol) ?: "" else ""
                    val sizeBytes = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "" else ""
                    val filePath = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val bitrate = if (bitrateCol != -1) cursor.getInt(bitrateCol) else 0
                    val dateAdded = if (dateAddedCol != -1) cursor.getLong(dateAddedCol) else 0L

                    audioFiles += AudioFile(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        albumArtUri = ContentUris.withAppendedId(albumArtBaseUri, albumId),
                        year = year,
                        trackNumber = trackNum,
                        genre = genre,
                        sizeBytes = sizeBytes,
                        mimeType = mimeType,
                        filePath = filePath,
                        bitrateBps = bitrate,
                        dateAdded = dateAdded
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return audioFiles
    }

    /**
     * Updates metadata for a specific audio file in MediaStore.
     * Handles Scoped Storage on API 29+.
     */
    fun updateMetadata(
        audioFile: AudioFile,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String = "",
        newYear: Int = 0,
        newTrackNumber: String = ""
    ): MetadataUpdateResult {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newTitle)
            put(MediaStore.Audio.Media.ARTIST, newArtist)
            put(MediaStore.Audio.Media.ALBUM, newAlbum)
            if (newGenre.isNotBlank()) put(MediaStore.Audio.Media.GENRE, newGenre)
            if (newYear > 0) put(MediaStore.Audio.Media.YEAR, newYear)
            if (newTrackNumber.isNotBlank()) put(MediaStore.Audio.Media.TRACK, newTrackNumber)
        }

        return try {
            val rowsUpdated = context.contentResolver.update(
                audioFile.contentUri,
                values,
                null,
                null
            )
            if (rowsUpdated > 0) MetadataUpdateResult.Success else MetadataUpdateResult.Failure
        } catch (e: SecurityException) {
            handleSecurityException(audioFile.contentUri)
        } catch (e: Exception) {
            e.printStackTrace()
            MetadataUpdateResult.Failure
        }
    }

    private fun handleSecurityException(uri: Uri): MetadataUpdateResult {
        // Since minSdk is 33, we only need to handle Android 11+ way
        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
        return MetadataUpdateResult.PermissionRequired(pendingIntent)
    }
}
