package com.vydra.app.di

import android.content.Context
import androidx.room.Room
import com.vydra.app.data.local.VydraDatabase
import com.vydra.app.data.local.dao.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VydraDatabase {
        return Room.databaseBuilder(
            context,
            VydraDatabase::class.java,
            "vydra_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: VydraDatabase): DownloadDao {
        return database.downloadDao()
    }
}
