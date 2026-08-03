package com.vydra.app.engine

import android.content.Context
import android.util.Log
import com.vydra.app.domain.model.FormatOption
import com.vydra.app.domain.model.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

class YtdlpEngine(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private var binaryPath: String? = null

    init {
        try {
            extractBinary()
        } catch (e: Exception) {
            Log.e("YtdlpEngine", "Failed to extract yt-dlp binary", e)
        }
    }

    private fun extractBinary() {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val ytdlpFile = File(binDir, "yt-dlp")
        if (!ytdlpFile.exists()) {
            try {
                context.assets.open("bin/yt-dlp").use { input ->
                    ytdlpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                ytdlpFile.setExecutable(true)
            } catch (e: Exception) {
                Log.e("YtdlpEngine", "yt-dlp asset not found in APK", e)
                return
            }
        }
        binaryPath = ytdlpFile.absolutePath
    }

    suspend fun getMediaInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val bp = binaryPath ?: return@withContext Result.failure(
                Exception("yt-dlp binary not found. The app may need to be reinstalled.")
            )

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
                return@withContext Result.failure(Exception("Failed to analyze: $output"))
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
        try {
            val bp = binaryPath ?: return@withContext Result.failure(
                Exception("yt-dlp binary not found")
            )

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
            var currentProcess: Process? = process

            try {
                while (reader.readLine()?.also { lastLine = it } != null) {
                    if (lastLine.contains("[download]") && lastLine.contains("%")) {
                        val percentStr = lastLine
                            .substringAfter("[download] ")
                            .substringBefore("%")
                            .trim()
                        val percent = percentStr.toFloatOrNull() ?: 0f

                        val speedStr = if (lastLine.contains("at ")) {
                            lastLine.substringAfter("at ").substringBefore(" ").trim()
                        } else ""

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
            } finally {
                currentProcess = null
            }
        } catch (e: Exception) {
            onError(e)
            Result.failure(e)
        }
    }
}
