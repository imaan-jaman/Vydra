package com.vydra.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VydraApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows download progress"
            setShowBadge(false)
        }

        val completedChannel = NotificationChannel(
            COMPLETED_CHANNEL_ID,
            "Completed Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows completed downloads"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(downloadChannel)
        manager.createNotificationChannel(completedChannel)
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "vydra_downloads"
        const val COMPLETED_CHANNEL_ID = "vydra_completed"
    }
}
