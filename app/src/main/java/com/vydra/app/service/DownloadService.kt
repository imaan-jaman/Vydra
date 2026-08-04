package com.vydra.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var ytdlpEngine: YtdlpEngine

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentDownload = MutableStateFlow<DownloadEntity?>(null)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private var lastDbWriteTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                startForeground(getNotificationId(downloadId), createPlaceholderNotification())
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

    private fun getDownloadDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Vydra"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun startDownload(downloadId: Long, url: String) {
        activeJobs[downloadId]?.cancel()

        activeJobs[downloadId] = serviceScope.launch {
            try {
                if (!ytdlpEngine.isReady) {
                    val updated = downloadRepository.getDownloadById(downloadId)?.copy(
                        status = "FAILED",
                        errorMessage = "yt-dlp is not installed. Please install it from Settings."
                    )
                    updated?.let { downloadRepository.updateDownload(it) }
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@launch
                }

                val download = downloadRepository.getDownloadById(downloadId) ?: run {
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    return@launch
                }
                _currentDownload.value = download

                downloadRepository.updateDownload(download.copy(status = "DOWNLOADING"))
                updateNotification(download)

                val outputDir = getDownloadDir()
                val safeName = download.title
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .take(100)
                    .ifEmpty { "download" }
                val ext = download.format.ifEmpty { "mp4" }
                val fileName = "$safeName.$ext"
                val tempFile = File(cacheDir, "dl_${downloadId}_$fileName")
                val finalFile = File(outputDir, fileName)

                val quality = download.quality
                val formatSpec = buildFormatSpec(quality, ext)

                Log.i("DownloadService", "Downloading: $url")
                Log.i("DownloadService", "Format spec: $formatSpec")
                Log.i("DownloadService", "Temp file: ${tempFile.absolutePath}")
                Log.i("DownloadService", "Final file: ${finalFile.absolutePath}")

                ytdlpEngine.download(
                    url = url,
                    formatSpec = formatSpec,
                    outputPath = tempFile.absolutePath,
                    onProgress = { progress, _, _ ->
                        val now = System.currentTimeMillis()
                        if (now - lastDbWriteTime > 500) {
                            lastDbWriteTime = now
                            serviceScope.launch {
                                try {
                                    downloadRepository.updateDownload(
                                        download.copy(progress = progress, status = "DOWNLOADING")
                                    )
                                    updateNotification(download.copy(progress = progress))
                                } catch (e: Exception) {
                                    Log.e("DownloadService", "Failed to update progress", e)
                                }
                            }
                        }
                    },
                    onComplete = { tempPath ->
                        serviceScope.launch {
                            try {
                                val savedPath = copyToPublicDownloads(tempFile, finalFile, download.mimeType)
                                val updated = download.copy(
                                    filePath = savedPath,
                                    status = "COMPLETED",
                                    progress = 100f,
                                    completedAt = System.currentTimeMillis()
                                )
                                downloadRepository.updateDownload(updated)
                                withContext(Dispatchers.Main) {
                                    showCompletedNotification(updated)
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                    stopSelf()
                                }
                            } catch (e: Exception) {
                                Log.e("DownloadService", "Failed to complete", e)
                                downloadRepository.updateDownload(
                                    download.copy(status = "FAILED", errorMessage = e.message)
                                )
                                withContext(Dispatchers.Main) {
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                    stopSelf()
                                }
                            }
                        }
                    },
                    onError = { error ->
                        serviceScope.launch {
                            try {
                                downloadRepository.updateDownload(
                                    download.copy(status = "FAILED", errorMessage = error.message)
                                )
                                withContext(Dispatchers.Main) {
                                    stopForeground(STOP_FOREGROUND_REMOVE)
                                    stopSelf()
                                }
                            } catch (e: Exception) {
                                Log.e("DownloadService", "Failed to handle error", e)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("DownloadService", "Download failed unexpectedly", e)
                try {
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildFormatSpec(quality: String, ext: String): String {
        return when {
            quality.contains("mp3", ignoreCase = true) ->
                "-x --audio-format mp3 --audio-quality 192K"
            quality.contains("m4a", ignoreCase = true) ->
                "-x --audio-format m4a --audio-quality 192K"
            quality.contains("4k", ignoreCase = true) ||
            quality.contains("2160", ignoreCase = true) ->
                "-f \"bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\""
            quality.contains("1080", ignoreCase = true) ->
                "-f \"bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\""
            quality.contains("720", ignoreCase = true) ->
                "-f \"bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\""
            quality.contains("480", ignoreCase = true) ->
                "-f \"bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\""
            quality.contains("360", ignoreCase = true) ->
                "-f \"bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\""
            else ->
                "-f \"bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\""
        }
    }

    private fun copyToPublicDownloads(tempFile: File, finalFile: File, mimeType: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyWithMediaStore(tempFile, finalFile, mimeType)
        } else {
            copyWithFile(tempFile, finalFile)
        }
    }

    private fun copyWithMediaStore(tempFile: File, finalFile: File, mimeType: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, finalFile.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifEmpty { "video/mp4" })
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Vydra")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return copyWithFile(tempFile, finalFile)

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(tempFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        tempFile.delete()
        return uri.toString()
    }

    private fun copyWithFile(tempFile: File, finalFile: File): String {
        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, finalFile.absolutePath)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }
        contentResolver.insert(MediaStore.Files.getContentUri("external"), values)

        return finalFile.absolutePath
    }

    private fun pauseDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        serviceScope.launch {
            try {
                val download = downloadRepository.getDownloadById(downloadId) ?: return@launch
                downloadRepository.updateDownload(download.copy(status = "PAUSED"))
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("DownloadService", "Failed to pause", e)
            }
        }
    }

    private fun cancelDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        serviceScope.launch {
            try {
                downloadRepository.deleteDownloadById(downloadId)
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("DownloadService", "Failed to cancel", e)
            }
        }
    }

    private fun getNotificationId(downloadId: Long): Int {
        return (NOTIFICATION_BASE_ID + downloadId).toInt()
    }

    private fun createPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, VydraApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Preparing download...")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(download: DownloadEntity) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, VydraApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(download.title)
            .setContentText("${download.progress.toInt()}%")
            .setProgress(100, download.progress.toInt(), false)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(getNotificationId(download.id), notification)
    }

    private fun showCompletedNotification(download: DownloadEntity) {
        if (download.filePath.isBlank()) return

        val fileUri = if (download.filePath.startsWith("content://")) {
            Uri.parse(download.filePath)
        } else {
            val file = File(download.filePath)
            if (!file.exists()) return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.fromFile(file),
                    download.mimeType.ifEmpty { "video/*" }
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return
        }

        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, download.mimeType.ifEmpty { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
