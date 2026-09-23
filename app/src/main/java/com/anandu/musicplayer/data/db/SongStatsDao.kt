package com.anandu.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongStatsDao {
    @Query("SELECT * FROM song_stats ORDER BY totalTimeListenedMs DESC, playCount DESC LIMIT :limit")
    fun getMostPlayedFlow(limit: Int = 50): Flow<List<SongStatsEntity>>

    @Query("SELECT * FROM song_stats ORDER BY totalTimeListenedMs DESC, playCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 50): List<SongStatsEntity>

    @Query("SELECT * FROM song_stats WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getRecentlyPlayedFlow(limit: Int = 50): Flow<List<SongStatsEntity>>

    @Query("SELECT * FROM song_stats WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 50): List<SongStatsEntity>

    @Query("SELECT * FROM song_stats WHERE trackUri = :trackUri")
    suspend fun getSongStats(trackUri: String): SongStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongStats(stats: SongStatsEntity)

    @Query("UPDATE song_stats SET totalTimeListenedMs = totalTimeListenedMs + :addTimeMs WHERE trackUri = :trackUri")
    suspend fun addListenedTime(trackUri: String, addTimeMs: Long)

    @Query("SELECT * FROM song_stats")
    suspend fun getAllStats(): List<SongStatsEntity>
}
