package com.anandu.musicplayer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(playlistSong: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND trackUri = :trackUri")
    suspend fun removeSongFromPlaylist(playlistId: Long, trackUri: String)

    @Query("SELECT * FROM playlist_songs")
    suspend fun getAllPlaylistSongs(): List<PlaylistSongEntity>
}
