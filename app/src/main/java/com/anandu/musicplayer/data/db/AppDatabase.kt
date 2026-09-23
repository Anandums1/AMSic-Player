package com.anandu.musicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlaylistEntity::class, PlaylistSongEntity::class, SongStatsEntity::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun songStatsDao(): SongStatsDao
}
