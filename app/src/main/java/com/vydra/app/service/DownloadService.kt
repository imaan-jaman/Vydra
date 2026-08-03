package com.vydra.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.vydra.app.R
import com.vydra.app.VydraApp
import com.vydra.app.data.local.entity.DownloadEntity
import com.vydra.app.domain.repository.DownloadRepository
import com.vydra.app.engine.YtdlpEngine
import com.vydra.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var ytdlpEngine: YtdlpEngine

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentDownload = MutableStateFlow<DownloadEntity?>(null)
    private var currentJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                startDownload(downloadId, url)
            }
            ACTION_PAUSE -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                pauseDownload(downloadId)
            }
            ACTION_CANCEL -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                cancelDownload(downloadId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(downloadId: Long, url: String) {
        currentJob?.cancel()
        currentJob = serviceScope.launch {
            try {
                val download = downloadRepository.getDownloadById(downloadId) ?: return@launch
                _currentDownload.value = download

                startForeground(getNotificationId(downloadId), createNotification(download))

                val outputDir = getExternalFilesDir(null) ?: filesDir
                val safeName = download.title
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .take(100)
                    .ifEmpty { "download" }
                val ext = download.format.ifEmpty { "mp4" }
                val fileName = "$safeName.$ext"
                val outputPath = File(outputDir, fileName).absolutePath

                val formatSpec = "-f bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"

                ytdlpEngine.download(
                    url = url,
                    formatSpec = formatSpec,
                    outputPath = outputPath,
                    onProgress = { progress, _, _ ->
                        serviceScope.launch {
                            try {
                                val updated = download.copy(
                                    progress = progress,
                                    status = "DOWNLOADING"
                                )
                                downloadRepository.updateDownload(updated)
                                updateNotification(updated)
                            } catch (e: Exception) {
                                Log.e("DownloadService", "Failed to update progress", e)
                            }
                        }
                    },
                    onComplete = { path ->
                        serviceScope.launch {
                            try {
                                val updated = download.copy(
                                    filePath = path,
                                    status = "COMPLETED",
                                    progress = 100f,
                                    completedAt = System.currentTimeMillis()
                                )
                                downloadRepository.updateDownload(updated)
                                showCompletedNotification(updated)
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            } catch (e: Exception) {
                                Log.e("DownloadService", "Failed to complete", e)
                            }
                        }
                    },
                    onError = { error ->
                        serviceScope.launch {
                            try {
                                val updated = download.copy(
                                    status = "FAILED",
                                    errorMessage = error.message
                                )
                                downloadRepository.updateDownload(updated)
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                            } catch (e: Exception) {
                                Log.e("DownloadService", "Failed to handle error", e)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("DownloadService", "Download failed unexpectedly", e)
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } catch (_: Exception) {}
            }
        }
    }

    private fun pauseDownload(downloadId: Long) {
        currentJob?.cancel()
        currentJob = null
        serviceScope.launch {
            try {
                val download = downloadRepository.getDownloadById(downloadId) ?: return@launch
                val updated = download.copy(status = "PAUSED")
                downloadRepository.updateDownload(updated)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e("DownloadService", "Failed to pause", e)
            }
        }
    }

    private fun cancelDownload(downloadId: Long) {
        currentJob?.cancel()
        currentJob = null
        serviceScope.launch {
            try {
                downloadRepository.deleteDownloadById(downloadId)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e("DownloadService", "Failed to cancel", e)
            }
        }
    }

    private fun getNotificationId(downloadId: Long): Int {
        return (NOTIFICATION_BASE_ID + downloadId).toInt()
    }

    private fun createNotification(download: DownloadEntity): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, VydraApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(download.title.ifEmpty { "Downloading..." })
            .setContentText("Starting download")
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(download: DownloadEntity) {
        val notification = NotificationCompat.Builder(this, VydraApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(download.title)
            .setContentText("${download.progress.toInt()}%")
            .setProgress(100, download.progress.toInt(), false)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(getNotificationId(download.id), notification)
    }

    private fun showCompletedNotification(download: DownloadEntity) {
        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(
                this@DownloadService,
                "${packageName}.fileprovider",
                File(download.filePath)
            )
            setDataAndType(uri, download.mimeType.ifEmpty { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            download.id.toInt(),
            fileIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, VydraApp.COMPLETED_CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(download.title)
            .setSmallIcon(R.drawable.ic_check)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(getNotificationId(download.id), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.vydra.app.DOWNLOAD_START"
        const val ACTION_PAUSE = "com.vydra.app.DOWNLOAD_PAUSE"
        const val ACTION_CANCEL = "com.vydra.app.DOWNLOAD_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_URL = "url"
        const val NOTIFICATION_BASE_ID = 10001

        fun startDownload(context: Context, downloadId: Long, url: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
}
