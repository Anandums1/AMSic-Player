package com.anandu.musicplayer

import android.app.Application
import com.anandu.musicplayer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class MusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MusicPlayerApp)
            modules(appModule)
        }
    }
}

