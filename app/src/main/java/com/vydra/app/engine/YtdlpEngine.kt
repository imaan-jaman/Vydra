package com.vydra.app.engine

import android.content.Context
import android.util.Log
import com.vydra.app.domain.model.FormatOption
import com.vydra.app.domain.model.MediaInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class YtdlpEngine(private val context: Context) {

    companion object {
        private const val TAG = "YtdlpEngine"
        private const val PROCESS_ID_PREFIX = "vydra_"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val updateMutex = Mutex()
    private val initMutex = Mutex()
    private val activeDownloads = ConcurrentHashMap<String, String>()
    private val processCounter = AtomicInteger(0)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    @Volatile
    private var initialized = false
    private var initError: String? = null

    val isReady: Boolean get() = initialized && initError == null

    init {
        Thread {
            try {
                runBlocking {
                    doInit()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Init thread crashed: ${e.message}", e)
                initError = e.message ?: e.toString()
                initialized = false
            }
        }.apply {
            name = "ytdlp-init"
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    private suspend fun doInit() {
        initMutex.withLock {
            if (initialized) return
            try {
                Log.i(TAG, "Initializing youtubedl-android library...")
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(context)
                }
                initialized = true
                initError = null
                Log.i(TAG, "youtubedl-android initialized successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Init failed: ${e.message}", e)
                initError = e.message ?: e.toString()
                initialized = false
            }
        }
    }

    private suspend fun ensureInit() {
        if (initialized) return
        doInit()
    }

    suspend fun ensureBinary(): Result<String> = withContext(Dispatchers.IO) {
        ensureInit()
        if (!isReady) {
            return@withContext Result.failure(
                Exception("yt-dlp engine failed to initialize: $initError")
            )
        }
        Result.success("ready")
    }

    suspend fun updateBinary(): Result<String> = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            try {
                ensureInit()
                _updateState.value = UpdateState.Downloading(0)
                Log.i(TAG, "Updating yt-dlp via library...")

                YoutubeDL.getInstance().updateYoutubeDL(context)
                val version = YoutubeDL.version(context) ?: "unknown"
                Log.i(TAG, "yt-dlp updated to: $version")

                _updateState.value = UpdateState.Success
                Result.success(version)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update yt-dlp", e)
                _updateState.value = UpdateState.Error(e.message ?: "Update failed")
                Result.failure(e)
            }
        }
    }

    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        try {
            ensureInit()
            if (!isReady) return@withContext "Not installed"
            YoutubeDL.version(context) ?: "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get version", e)
            "Error: ${e.message}"
        }
    }

    suspend fun getMediaInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        ensureInit()
        if (!isReady) {
            return@withContext Result.failure(
                Exception("yt-dlp not available: $initError")
            )
        }

        try {
            Log.i(TAG, "Fetching media info for: $url")

            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--no-download")
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificates")

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(request)
            val output = response.out

            if (response.exitCode > 0) {
                val errorMsg = output.lines().lastOrNull() ?: "Unknown error"
                Log.e(TAG, "yt-dlp info failed (exit ${response.exitCode}): $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonOutput = json.parseToJsonElement(output) as JsonObject

            val formats = mutableListOf<FormatOption>()
            jsonOutput["formats"]?.jsonArray?.forEach { format ->
                val obj = format.jsonObject
                formats.add(
                    FormatOption(
                        formatId = obj["format_id"]?.jsonPrimitive?.content ?: "",
                        extension = obj["ext"]?.jsonPrimitive?.content ?: "",
                        resolution = obj["resolution"]?.jsonPrimitive?.content ?: "",
                        fps = obj["fps"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        vcodec = obj["vcodec"]?.jsonPrimitive?.content ?: "none",
                        acodec = obj["acodec"]?.jsonPrimitive?.content ?: "none",
                        filesize = obj["filesize"]?.jsonPrimitive?.content?.toLongOrNull()
                            ?: obj["filesize_approx"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        bitrate = obj["tbr"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        audioBitrate = obj["abr"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        quality = obj["format_note"]?.jsonPrimitive?.content ?: "",
                        hasVideo = obj["vcodec"]?.jsonPrimitive?.content != "none",
                        hasAudio = obj["acodec"]?.jsonPrimitive?.content != "none",
                        isHdr = obj["dynamic_range"]?.jsonPrimitive?.content == "HDR"
                    )
                )
            }

            val info = MediaInfo(
                url = url,
                title = jsonOutput["title"]?.jsonPrimitive?.content ?: "",
                thumbnail = jsonOutput["thumbnail"]?.jsonPrimitive?.content ?: "",
                duration = jsonOutput["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                uploader = jsonOutput["uploader"]?.jsonPrimitive?.content ?: "",
                website = jsonOutput["extractor"]?.jsonPrimitive?.content ?: "",
                description = jsonOutput["description"]?.jsonPrimitive?.content ?: "",
                uploadDate = jsonOutput["upload_date"]?.jsonPrimitive?.content ?: "",
                viewCount = jsonOutput["view_count"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                likeCount = jsonOutput["like_count"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                formats = formats.filter { it.hasVideo },
                audioFormats = formats.filter { it.hasAudio && !it.hasVideo }
            )

            Log.i(TAG, "Media info fetched: ${info.title}")
            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get media info", e)
            Result.failure(e)
        }
    }

    suspend fun download(
        url: String,
        formatArgs: List<String> = listOf("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"),
        outputPath: String,
        onProgress: (Float, Long, Long) -> Unit = { _: Float, _: Long, _: Long -> },
        onComplete: (String) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureInit()
        if (!isReady) {
            return@withContext Result.failure(Exception("yt-dlp not installed"))
        }

        val processId = "$PROCESS_ID_PREFIX${System.currentTimeMillis()}_${processCounter.getAndIncrement()}"
        activeDownloads[processId] = url

        try {
            Log.i(TAG, "Starting download: $url -> $outputPath (processId: $processId)")

            val request = YoutubeDLRequest(url)
            formatArgs.forEach { request.addOption(it) }
            request.addOption("-o", outputPath)
            request.addOption("--newline")
            request.addOption("--no-warnings")
            request.addOption("--no-check-certificates")
            request.addOption("--progress")

            val response: YoutubeDLResponse = YoutubeDL.getInstance().execute(
                request,
                processId
            ) { progress: Float, eta: Long, line: String ->
                onProgress(progress, 0L, eta)
            }

            activeDownloads.remove(processId)

            if (response.exitCode > 0 && response.exitCode != 130) {
                val errorMsg = response.err.ifEmpty { response.out.lines().lastOrNull() ?: "Unknown error" }
                Log.e(TAG, "Download failed (exit ${response.exitCode}): $errorMsg")
                onError(Exception(errorMsg))
                return@withContext Result.failure(Exception(errorMsg))
            }

            Log.i(TAG, "Download complete: $outputPath")
            onComplete(outputPath)
            Result.success(outputPath)
        } catch (e: Exception) {
            activeDownloads.remove(processId)
            Log.e(TAG, "Download failed", e)
            onError(e)
            Result.failure(e)
        }
    }

    fun cancelDownload(processId: String): Boolean {
        activeDownloads.remove(processId)
        return try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel download", e)
            false
        }
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}
