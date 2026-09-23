package com.anandu.musicplayer.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetKeys {
    val TITLE = stringPreferencesKey("widget_title")
    val ARTIST = stringPreferencesKey("widget_artist")
    val ALBUM = stringPreferencesKey("widget_album")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val ALBUM_ART_URI = stringPreferencesKey("widget_album_art_uri")
    val IS_FAVORITE = booleanPreferencesKey("widget_is_favorite")
    val SHUFFLE_MODE = booleanPreferencesKey("widget_shuffle_mode")
    val REPEAT_MODE = intPreferencesKey("widget_repeat_mode")
    val DURATION_MS = longPreferencesKey("widget_duration_ms")
    val POSITION_MS = longPreferencesKey("widget_position_ms")
    val DOMINANT_COLOR = intPreferencesKey("widget_dominant_color")
    val HAS_TRACK = booleanPreferencesKey("widget_has_track")
}

