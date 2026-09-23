package com.anandu.musicplayer.di

import android.app.Application
import androidx.room.Room
import com.anandu.musicplayer.data.MediaStoreDataSource
import com.anandu.musicplayer.data.db.AppDatabase
import com.anandu.musicplayer.data.SettingsManager
import com.anandu.musicplayer.ui.PlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "amsic_player_db"
        ).fallbackToDestructiveMigration(true).build()
    }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().songStatsDao() }
    single { SettingsManager(androidContext()) }
    single { MediaStoreDataSource(get()) }
    viewModel { PlayerViewModel(androidContext() as Application, get(), get(), get(), get()) }
}
