package com.anandu.musicplayer.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.IOException

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
     * Updates metadata for a specific audio file.
     * Modifies embedded audio tags (ID3v2, Vorbis, etc.) via a sandbox cache copy,
     * streams changes back via ContentResolver (handling Scoped Storage on API 30+),
     * updates MediaStore database records, and triggers MediaScanner to re-index the file.
     */
    suspend fun updateMetadata(
        audioFile: AudioFile,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String = "",
        newYear: Int = 0,
        newTrackNumber: String = ""
    ): MetadataUpdateResult {
        val trimmedTitle = newTitle.trim()
        val trimmedArtist = newArtist.trim()
        val trimmedAlbum = newAlbum.trim()
        val trimmedGenre = newGenre.trim()
        val trimmedTrackNumber = newTrackNumber.trim()

        var fileWriteSuccess = false
        val originalFile = File(audioFile.filePath)
        val extension = if (originalFile.extension.isNotBlank()) {
            originalFile.extension
        } else {
            when (audioFile.mimeType) {
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/flac" -> "flac"
                "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
                "audio/ogg" -> "ogg"
                "audio/x-wav", "audio/wav" -> "wav"
                else -> "mp3"
            }
        }

        val tempFile = File(
            context.cacheDir,
            "meta_edit_${audioFile.id}_${System.currentTimeMillis()}.$extension"
        )

        try {
            // 1. Copy source audio to app cache for safe in-sandbox tag manipulation
            var cacheCopySuccess = false
            try {
                context.contentResolver.openInputStream(audioFile.contentUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                cacheCopySuccess = tempFile.exists() && tempFile.length() > 0
            } catch (e: Exception) {
                Log.e("MediaStoreDataSource", "Failed to copy audio stream to cache", e)
            }

            // 2. Modify embedded tags using JAudioTagger in private cache
            var tagEditSuccess = false
            if (cacheCopySuccess) {
                try {
                    val af = AudioFileIO.read(tempFile)
                    val tag = af.tagOrCreateAndSetDefault
                    if (trimmedTitle.isNotBlank()) tag.setField(FieldKey.TITLE, trimmedTitle)
                    if (trimmedArtist.isNotBlank()) tag.setField(FieldKey.ARTIST, trimmedArtist)
                    if (trimmedAlbum.isNotBlank()) tag.setField(FieldKey.ALBUM, trimmedAlbum)
                    if (trimmedGenre.isNotBlank()) {
                        tag.setField(FieldKey.GENRE, trimmedGenre)
                    } else {
                        try { tag.deleteField(FieldKey.GENRE) } catch (_: Exception) {}
                    }
                    if (newYear > 0) {
                        tag.setField(FieldKey.YEAR, newYear.toString())
                    } else {
                        try { tag.deleteField(FieldKey.YEAR) } catch (_: Exception) {}
                    }
                    if (trimmedTrackNumber.isNotBlank()) {
                        tag.setField(FieldKey.TRACK, trimmedTrackNumber)
                    } else {
                        try { tag.deleteField(FieldKey.TRACK) } catch (_: Exception) {}
                    }
                    af.commit()
                    tagEditSuccess = true
                } catch (t: Throwable) {
                    Log.w("MediaStoreDataSource", "JAudioTagger could not write embedded tags: ${t.message}")
                }
            }

            // 3. Stream modified cache file back to the MediaStore contentUri
            if (tagEditSuccess) {
                try {
                    context.contentResolver.openOutputStream(audioFile.contentUri, "wt")?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Could not open output stream for ${audioFile.contentUri}")
                    fileWriteSuccess = true
                } catch (e: SecurityException) {
                    return handleSecurityException(audioFile.contentUri)
                } catch (e: Exception) {
                    Log.e("MediaStoreDataSource", "Failed to write updated tags back to content URI", e)
                }
            }

            // 4. Update MediaStore database record
            val values = ContentValues().apply {
                if (trimmedTitle.isNotBlank()) put(MediaStore.Audio.Media.TITLE, trimmedTitle)
                if (trimmedArtist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, trimmedArtist)
                if (trimmedAlbum.isNotBlank()) put(MediaStore.Audio.Media.ALBUM, trimmedAlbum)
                if (trimmedGenre.isNotBlank()) {
                    put(MediaStore.Audio.Media.GENRE, trimmedGenre)
                } else {
                    putNull(MediaStore.Audio.Media.GENRE)
                }
                if (newYear > 0) {
                    put(MediaStore.Audio.Media.YEAR, newYear)
                } else {
                    putNull(MediaStore.Audio.Media.YEAR)
                }
                val trackNumInt = trimmedTrackNumber.takeWhile { it.isDigit() }.toIntOrNull()
                if (trackNumInt != null && trackNumInt > 0) {
                    put(MediaStore.Audio.Media.TRACK, trackNumInt)
                } else {
                    putNull(MediaStore.Audio.Media.TRACK)
                }
            }

            val rowsUpdated = try {
                context.contentResolver.update(
                    audioFile.contentUri,
                    values,
                    null,
                    null
                )
            } catch (e: SecurityException) {
                return handleSecurityException(audioFile.contentUri)
            } catch (e: Exception) {
                Log.e("MediaStoreDataSource", "Failed to update MediaStore row", e)
                0
            }

            // 5. Notify MediaScanner to re-index the file ONLY IF physical file was written
            // (If physical write failed, running scanner would revert the database update!)
            if (fileWriteSuccess) {
                val pathToScan = if (originalFile.exists()) {
                    originalFile.absolutePath
                } else {
                    audioFile.filePath
                }
                if (pathToScan.isNotBlank()) {
                    scanFileAndWait(pathToScan)
                }
            }

            return if (rowsUpdated > 0 || fileWriteSuccess) {
                MetadataUpdateResult.Success
            } else {
                MetadataUpdateResult.Failure
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun scanFileAndWait(path: String): Uri? {
        return withTimeoutOrNull(3000L) {
            suspendCancellableCoroutine { continuation ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    null
                ) { _, uri ->
                    if (continuation.isActive) {
                        continuation.resume(uri)
                    }
                }
            }
        }
    }

    private fun handleSecurityException(uri: Uri): MetadataUpdateResult {
        // Since minSdk is 33, we only need to handle Android 11+ way
        val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
        return MetadataUpdateResult.PermissionRequired(pendingIntent)
    }
}
