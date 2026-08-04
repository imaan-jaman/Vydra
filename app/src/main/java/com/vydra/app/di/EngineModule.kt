package com.vydra.app.di

import android.content.Context
import android.util.Log
import com.vydra.app.engine.YtdlpEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideYtdlpEngine(@ApplicationContext context: Context): YtdlpEngine {
        val engine = YtdlpEngine(context)
        engine.initLibrary()
        Log.i("EngineModule", "YtdlpEngine provided and initialized")
        return engine
    }
}
