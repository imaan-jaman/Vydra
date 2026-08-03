package com.vydra.app.domain.usecase

import android.content.Context
import com.vydra.app.data.local.entity.DownloadEntity
import com.vydra.app.domain.model.DownloadRequest
import com.vydra.app.domain.model.MediaInfo
import com.vydra.app.domain.repository.DownloadRepository
import com.vydra.app.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class StartDownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(
        mediaInfo: MediaInfo,
        request: DownloadRequest
    ): Long {
        val formatExt = when {
            request.quality.contains("mp3", ignoreCase = true) -> "mp3"
            request.quality.contains("m4a", ignoreCase = true) -> "m4a"
            request.quality.contains("flac", ignoreCase = true) -> "flac"
            request.quality.contains("ogg", ignoreCase = true) -> "ogg"
            request.quality.contains("wav", ignoreCase = true) -> "wav"
            request.quality.contains("webm", ignoreCase = true) -> "webm"
            request.quality.contains("mkv", ignoreCase = true) -> "mkv"
            else -> "mp4"
        }

        val mimeType = when (formatExt) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }

        val entity = DownloadEntity(
            url = request.url,
            title = mediaInfo.title,
            thumbnail = mediaInfo.thumbnail,
            duration = mediaInfo.duration,
            uploader = mediaInfo.uploader,
            website = mediaInfo.website,
            format = formatExt,
            quality = request.quality,
            mimeType = mimeType,
            status = "QUEUED"
        )

        val id = downloadRepository.insertDownload(entity)

        DownloadService.startDownload(context, id, request.url)

        return id
    }
}
