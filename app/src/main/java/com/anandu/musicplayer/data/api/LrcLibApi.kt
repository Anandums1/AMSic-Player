package com.anandu.musicplayer.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("get")
    suspend fun getLyrics(
        @Query("track_name") track: String,
        @Query("artist_name") artist: String,
        @Query("album_name") album: String?,
        @Query("duration") duration: Int?
    ): LyricsResponse
}

data class LyricsResponse(
    val id: Long?,
    val name: String?,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Double?,
    val instrumental: Boolean?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
