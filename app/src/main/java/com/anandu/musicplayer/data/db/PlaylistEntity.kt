package com.anandu.musicplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "trackUri"],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val trackUri: String // Using contentUri as a stable identifier for songs
)
