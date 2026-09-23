# 🎵 AMSic Player

**AMSic Player** is a modern, lightweight, and feature-rich Android music player application built from the ground up using **Jetpack Compose**, **Material 3**, and **AndroidX Media3 (ExoPlayer)**.

---

## ✨ Features

- 🎧 **Media3 Playback Engine**: Powered by AndroidX Media3 (ExoPlayer) with full background playback support via `MediaSessionService`, system notification controls, and lock screen integration.
- 🎨 **Material 3 & Adaptive Design**: Sleek Compose UI with dynamic album art color extraction (Palette API), responsive layouts for phones, foldables, and tablets, and animated equalizer indicators.
- 🎤 **Synchronized Lyrics**: Automatic lyrics fetching via LRCLIB API integration with LRC synchronized parsing and auto-scrolling lyrics view.
- 📂 **Local Library Scanning**: Automatic MediaStore scanning for local audio files with fast alphabet-scrubber scrolling.
- 📊 **Playlists & Favorites**: Persistence backed by Room Database for custom playlists, favorite tracks, and song playback statistics.
- 📱 **Home Screen Widget**: Built with **Jetpack Glance** and Material 3 for home screen track display and playback controls.
- ⚡ **Gesture Controls**: Swipeable track rows for quick actions (favorite, queueing, track options bottom sheet).

---

## 🛠 Tech Stack & Libraries

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Audio Engine**: [AndroidX Media3](https://developer.android.com/media/media3) (ExoPlayer & MediaSession)
- **Dependency Injection**: [Koin](https://insert-koin.io/)
- **Database / Storage**: [Room Database](https://developer.android.com/training/data-storage/room) & Jetpack DataStore
- **Image Loading**: [Coil 3](https://coil-kt.github.io/coil/)
- **Networking**: [Retrofit 2](https://square.github.io/retrofit/) & OkHttp 3 for LRCLIB API
- **App Widgets**: [Jetpack Glance](https://developer.android.com/jetpack/compose/glance)
- **Palette**: AndroidX Palette for dynamic UI color matching

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Android Studio Ladybug / 2026.1.1 or newer
- **JDK**: Java 17
- **Min SDK**: Android 13 (API 33)
- **Target SDK**: Android 16 (API 37)

### Building the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/Anandums1/AMSic-Player.git
   cd AMSic-Player
   ```
2. Open the project in Android Studio.
3. Sync Gradle project files (`File -> Sync Project with Gradle Files`).
4. Connect an Android device or launch an emulator (API 33+).
5. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📄 License

This project is open source and available under the [Apache License 2.0](LICENSE).
