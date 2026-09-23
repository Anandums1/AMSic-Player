package com.anandu.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_stats")
data class SongStatsEntity(
    @PrimaryKey val trackUri: String,
    val playCount: Int = 0,
    val totalTimeListenedMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L
)
