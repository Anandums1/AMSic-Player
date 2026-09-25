# ── Retrofit + Gson ──────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.anandu.musicplayer.data.api.LyricsResponse { *; }
-dontwarn retrofit2.**
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Room ─────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ── Media3 ───────────────────────────────────────────────────────────────
# Keep critical Media3 classes, interfaces, and methods from being stripped/obfuscated
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep interface androidx.media3.common.** { *; }
-keep interface androidx.media3.session.** { *; }

# Maintain session service entry points
-keep class androidx.media3.session.MediaSessionService { *; }
-keep class androidx.media3.session.MediaLibraryService { *; }

# Suppress warnings for optional/unused Media3 dependencies if any
-dontwarn androidx.media3.cast.**
-dontwarn androidx.media3.datasource.cronet.**

# ── Coil ─────────────────────────────────────────────────────────────────
-dontwarn coil3.**

# ── Kotlin ───────────────────────────────────────────────────────────────
-keepclassmembers class kotlin.Metadata { *; }

# ── Jaudiotagger ─────────────────────────────────────────────────────────
-keep class org.jaudiotagger.** { *; }

