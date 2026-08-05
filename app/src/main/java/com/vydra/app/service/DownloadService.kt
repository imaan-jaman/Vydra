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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var ytdlpEngine: YtdlpEngine
    @Inject lateinit var downloadRepository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _currentDownload = MutableStateFlow<DownloadEntity?>(null)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeTempFiles = ConcurrentHashMap<Long, File>()
    private var lastDbWriteTime = 0L
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                startForeground(getNotificationId(downloadId), createPlaceholderNotification())
                isForeground = true
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
            var tempFile: File? = null
            try {
                if (!ytdlpEngine.isReady) {
                    downloadRepository.getDownloadById(downloadId)?.let { download ->
                        downloadRepository.updateDownload(
                            download.copy(status = "FAILED", errorMessage = "yt-dlp engine not ready")
                        )
                    }
                    stopServiceSafe()
                    return@launch
                }

                val download = downloadRepository.getDownloadById(downloadId) ?: run {
                    stopServiceSafe()
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
                tempFile = File(cacheDir, "dl_${downloadId}_$fileName")
                activeTempFiles[downloadId] = tempFile!!

                val finalFile = File(outputDir, fileName)
                val quality = download.quality
                val formatArgs = buildFormatArgs(quality, ext)

                Log.i(TAG, "Starting download: $url")
                Log.i(TAG, "Format args: $formatArgs")
                Log.i(TAG, "Output: ${tempFile!!.absolutePath}")

                ytdlpEngine.download(
                    url = url,
                    formatArgs = formatArgs,
                    outputPath = tempFile!!.absolutePath,
                    onProgress = { progress, _, _ ->
                        val now = System.currentTimeMillis()
                        if (now - lastDbWriteTime > 500) {
                            lastDbWriteTime = now
                            val percent = (progress * 100f).coerceIn(0f, 100f)
                            serviceScope.launch {
                                try {
                                    downloadRepository.updateDownload(
                                        download.copy(progress = percent, status = "DOWNLOADING")
                                    )
                                    updateNotification(download.copy(progress = percent))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to update progress", e)
                                }
                            }
                        }
                    },
                    onComplete = { _ ->
                        serviceScope.launch {
                            try {
                                val savedPath = saveToDownloads(tempFile!!, finalFile, download.mimeType)
                                activeTempFiles.remove(downloadId)
                                tempFile = null
                                downloadRepository.updateDownload(
                                    download.copy(
                                        filePath = savedPath,
                                        status = "COMPLETED",
                                        progress = 100f,
                                        completedAt = System.currentTimeMillis()
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    val updated = download.copy(filePath = savedPath, status = "COMPLETED")
                                    showCompletedNotification(updated)
                                    stopServiceSafe()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save download", e)
                                downloadRepository.updateDownload(
                                    download.copy(status = "FAILED", errorMessage = e.message)
                                )
                                stopServiceSafe()
                            }
                        }
                    },
                    onError = { error ->
                        serviceScope.launch {
                            activeTempFiles.remove(downloadId)
                            tempFile?.delete()
                            tempFile = null
                            downloadRepository.updateDownload(
                                download.copy(status = "FAILED", errorMessage = error.message)
                            )
                            stopServiceSafe()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download failed unexpectedly", e)
                activeTempFiles.remove(downloadId)
                tempFile?.delete()
                try {
                    downloadRepository.getDownloadById(downloadId)?.let { download ->
                        downloadRepository.updateDownload(
                            download.copy(status = "FAILED", errorMessage = e.message)
                        )
                    }
                } catch (_: Exception) {}
                stopServiceSafe()
            }
        }
    }

    private fun buildFormatArgs(quality: String, ext: String): List<String> {
        return when {
            quality.contains("mp3", ignoreCase = true) ->
                listOf("-x", "--audio-format", "mp3", "--audio-quality", "192K")
            quality.contains("m4a", ignoreCase = true) ->
                listOf("-x", "--audio-format", "m4a", "--audio-quality", "192K")
            quality.contains("4k", ignoreCase = true) ||
            quality.contains("2160", ignoreCase = true) ->
                listOf("-f", "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            quality.contains("1080", ignoreCase = true) ->
                listOf("-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            quality.contains("720", ignoreCase = true) ->
                listOf("-f", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            quality.contains("480", ignoreCase = true) ->
                listOf("-f", "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            quality.contains("360", ignoreCase = true) ->
                listOf("-f", "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            else ->
                listOf("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
        }
    }

    private fun saveToDownloads(tempFile: File, finalFile: File, mimeType: String): String {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw Exception("Downloaded file is empty or missing")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(tempFile, finalFile.name, mimeType)
        } else {
            saveViaDirectCopy(tempFile, finalFile, mimeType)
        }
    }

    private fun saveViaMediaStore(tempFile: File, fileName: String, mimeType: String): String {
        val resolvedMimeType = when {
            mimeType.isNotEmpty() && mimeType != "video/mp4" -> mimeType
            fileName.endsWith(".mp3", true) -> "audio/mpeg"
            fileName.endsWith(".m4a", true) -> "audio/mp4"
            fileName.endsWith(".webm", true) -> "video/webm"
            fileName.endsWith(".mkv", true) -> "video/x-matroska"
            else -> "video/mp4"
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, resolvedMimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Vydra")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(tempFile).use { inputStream ->
                inputStream.copyTo(outputStream, bufferSize = 8192)
            }
        } ?: throw Exception("Failed to open output stream for MediaStore")

        tempFile.delete()
        return uri.toString()
    }

    private fun saveViaDirectCopy(tempFile: File, finalFile: File, mimeType: String): String {
        tempFile.copyTo(finalFile, overwrite = true)
        tempFile.delete()

        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, finalFile.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, when {
                    finalFile.name.endsWith(".mp3", true) -> "audio/mpeg"
                    finalFile.name.endsWith(".m4a", true) -> "audio/mp4"
                    else -> "video/mp4"
                })
            }
            contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore insert failed for pre-Q", e)
        }

        return finalFile.absolutePath
    }

    private fun pauseDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        activeTempFiles.remove(downloadId)?.delete()
        serviceScope.launch {
            try {
                val download = downloadRepository.getDownloadById(downloadId) ?: return@launch
                downloadRepository.updateDownload(download.copy(status = "PAUSED"))
                stopServiceSafe()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause", e)
            }
        }
    }

    private fun cancelDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        activeTempFiles.remove(downloadId)?.delete()
        serviceScope.launch {
            try {
                downloadRepository.deleteDownloadById(downloadId)
                stopServiceSafe()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel", e)
            }
        }
    }

    private fun stopServiceSafe() {
        if (isForeground) {
            try {
                isForeground = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
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
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val progressPercent = download.progress.toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(this, VydraApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(download.title)
            .setContentText("$progressPercent%")
            .setProgress(100, progressPercent, false)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(getNotificationId(download.id), notification)
    }

    private fun showCompletedNotification(download: DownloadEntity) {
        val fileUri = when {
            download.filePath.isBlank() -> return
            download.filePath.startsWith("content://") -> Uri.parse(download.filePath)
            else -> {
                val file = File(download.filePath)
                if (!file.exists()) return
                try {
                    FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "FileProvider failed, using file URI", e)
                    Uri.fromFile(file)
                }
            }
        }

        val fileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, download.mimeType.ifEmpty { "video/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, download.id.toInt(), fileIntent,
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
        activeTempFiles.values.forEach { it.delete() }
        activeTempFiles.clear()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "DownloadService"
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
