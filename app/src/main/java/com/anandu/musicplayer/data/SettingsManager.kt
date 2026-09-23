package com.anandu.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _excludeShortClips = MutableStateFlow(prefs.getBoolean(KEY_EXCLUDE_SHORT_CLIPS, true))
    val excludeShortClips: StateFlow<Boolean> = _excludeShortClips.asStateFlow()

    private val _vinylMode = MutableStateFlow(prefs.getBoolean(KEY_VINYL_MODE, false))
    val vinylMode: StateFlow<Boolean> = _vinylMode.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _autoEmbedLyrics = MutableStateFlow(prefs.getBoolean(KEY_AUTO_EMBED_LYRICS, true))
    val autoEmbedLyrics: StateFlow<Boolean> = _autoEmbedLyrics.asStateFlow()

    private val _pauseOnDisconnect = MutableStateFlow(prefs.getBoolean(KEY_PAUSE_ON_DISCONNECT, true))
    val pauseOnDisconnect: StateFlow<Boolean> = _pauseOnDisconnect.asStateFlow()

    private val _swipeGesturesEnabled = MutableStateFlow(prefs.getBoolean(KEY_SWIPE_GESTURES, true))
    val swipeGesturesEnabled: StateFlow<Boolean> = _swipeGesturesEnabled.asStateFlow()

    fun setExcludeShortClips(exclude: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_SHORT_CLIPS, exclude).apply()
        _excludeShortClips.value = exclude
    }

    fun setVinylMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VINYL_MODE, enabled).apply()
        _vinylMode.value = enabled
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _hapticFeedback.value = enabled
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setAutoEmbedLyrics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_EMBED_LYRICS, enabled).apply()
        _autoEmbedLyrics.value = enabled
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_ON_DISCONNECT, enabled).apply()
        _pauseOnDisconnect.value = enabled
    }

    fun setSwipeGesturesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_GESTURES, enabled).apply()
        _swipeGesturesEnabled.value = enabled
    }

    companion object {
        private const val KEY_EXCLUDE_SHORT_CLIPS = "exclude_short_clips"
        private const val KEY_VINYL_MODE = "vinyl_mode"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AUTO_EMBED_LYRICS = "auto_embed_lyrics"
        private const val KEY_PAUSE_ON_DISCONNECT = "pause_on_disconnect"
        private const val KEY_SWIPE_GESTURES = "swipe_gestures"
    }
}
