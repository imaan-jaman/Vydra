package com.vydra.app.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.vydra.app.domain.model.FormatOption
import com.vydra.app.domain.model.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class YtdlpEngine(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val updateMutex = Mutex()

    private val binDir: File by lazy {
        context.getDir("bin", Context.MODE_PRIVATE).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    private val ytdlpFile: File by lazy {
        File(binDir, "yt-dlp")
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var cachedReady = false

    val isReady: Boolean get() {
        if (cachedReady && ytdlpFile.exists() && ytdlpFile.length() > 100_000) return true
        val ready = checkBinaryReady()
        cachedReady = ready
        return ready
    }

    private fun checkBinaryReady(): Boolean {
        if (!ytdlpFile.exists()) return false
        if (ytdlpFile.length() < 100_000) return false
        ensureExecutable()
        return ytdlpFile.canExecute()
    }

    private fun ensureExecutable() {
        if (ytdlpFile.canExecute()) return
        try { ytdlpFile.setExecutable(true, false) } catch (_: Exception) {}
        if (ytdlpFile.canExecute()) return
        try {
            val p = Runtime.getRuntime().exec(arrayOf("chmod", "755", ytdlpFile.absolutePath))
            p.waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {}
        if (ytdlpFile.canExecute()) return
        try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "chmod 755 ${ytdlpFile.absolutePath}"))
            p.waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }

    fun getBinaryPath(): String? {
        return if (isReady) ytdlpFile.absolutePath else null
    }

    private fun getArchitecture(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
            abi.contains("arm") -> "armv7l"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> "aarch64"
        }
    }

    private fun getDownloadUrl(): String {
        val arch = getArchitecture()
        return "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_$arch"
    }

    suspend fun ensureBinary(): Result<String> = withContext(Dispatchers.IO) {
        val path = getBinaryPath()
        if (path != null) return@withContext Result.success(path)
        updateBinary()
    }

    suspend fun updateBinary(): Result<String> = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            try {
                _updateState.value = UpdateState.Downloading(0)

                val url = getDownloadUrl()
                Log.i("YtdlpEngine", "Downloading yt-dlp from: $url (arch: ${getArchitecture()})")

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent", "Vydra/1.0")
                connection.connect()

                if (connection.responseCode != 200) {
                    val msg = "HTTP ${connection.responseCode} from $url"
                    _updateState.value = UpdateState.Error(msg)
                    return@withContext Result.failure(Exception(msg))
                }

                val totalSize = connection.contentLength.toLong()
                var downloaded = 0L

                val tempFile = File(binDir, "yt-dlp.tmp")
                if (tempFile.exists()) tempFile.delete()

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val progress = if (totalSize > 0) (downloaded * 100 / totalSize).toInt() else 0
                            _updateState.value = UpdateState.Downloading(progress)
                        }
                    }
                }
                connection.disconnect()

                if (tempFile.length() < 100_000) {
                    tempFile.delete()
                    _updateState.value = UpdateState.Error("Downloaded file too small (${tempFile.length()} bytes)")
                    return@withContext Result.failure(Exception("Binary too small"))
                }

                if (ytdlpFile.exists()) ytdlpFile.delete()

                if (!tempFile.renameTo(ytdlpFile)) {
                    tempFile.copyTo(ytdlpFile, overwrite = true)
                    tempFile.delete()
                }

                ensureExecutable()

                if (!ytdlpFile.canExecute()) {
                    Log.e("YtdlpEngine", "All chmod attempts failed")
                    _updateState.value = UpdateState.Error("Cannot set execute permission on binary")
                    return@withContext Result.failure(Exception("Permission denied"))
                }

                cachedReady = true
                _updateState.value = UpdateState.Success
                Log.i("YtdlpEngine", "Binary ready at: ${ytdlpFile.absolutePath}")
                Result.success(ytdlpFile.absolutePath)
            } catch (e: Exception) {
                Log.e("YtdlpEngine", "Failed to update yt-dlp", e)
                _updateState.value = UpdateState.Error(e.message ?: "Update failed")
                Result.failure(e)
            }
        }
    }

    suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        try {
            val path = getBinaryPath() ?: return@withContext "Not installed"
            val process = ProcessBuilder(listOf(path, "--version"))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(5, TimeUnit.SECONDS)
            output.ifEmpty { "Unknown" }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun getMediaInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val bp = getBinaryPath()
            ?: return@withContext Result.failure(
                Exception("yt-dlp not installed. Tap the button in Settings to install it.")
            )

        try {
            val command = listOf(
                bp,
                "--dump-json",
                "--no-download",
                "--no-warnings",
                "--no-check-certificates",
                url
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(60, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return@withContext Result.failure(Exception("Analysis timed out"))
            }

            if (process.exitValue() != 0) {
                val errorMsg = output.lines().lastOrNull() ?: "Unknown error"
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
                        filesize = obj["filesize"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
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

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(
        url: String,
        formatSpec: String,
        outputPath: String,
        onProgress: (Float, Long, Long) -> Unit = { _: Float, _: Long, _: Long -> },
        onComplete: (String) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val bp = getBinaryPath()
            ?: return@withContext Result.failure(Exception("yt-dlp not installed"))

        try {
            val command = mutableListOf(
                bp,
                formatSpec,
                "-o", outputPath,
                "--newline",
                "--no-warnings",
                "--no-check-certificates",
                "--progress",
                url
            )

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val reader = process.inputStream.bufferedReader()
            var lastLine = ""

            while (reader.readLine()?.also { lastLine = it } != null) {
                if (lastLine.contains("[download]") && lastLine.contains("%")) {
                    val percentStr = lastLine
                        .substringAfter("[download] ")
                        .substringBefore("%")
                        .trim()
                    val percent = percentStr.toFloatOrNull() ?: 0f
                    onProgress(percent, 0, 0)
                }
            }

            val completed = process.waitFor(30, TimeUnit.MINUTES)
            if (!completed) {
                process.destroyForcibly()
                return@withContext Result.failure(Exception("Download timed out"))
            }

            if (process.exitValue() != 0) {
                return@withContext Result.failure(Exception("Download failed: $lastLine"))
            }

            onComplete(outputPath)
            Result.success(outputPath)
        } catch (e: Exception) {
            onError(e)
            Result.failure(e)
        }
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}
