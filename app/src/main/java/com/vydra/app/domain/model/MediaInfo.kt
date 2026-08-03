package com.vydra.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaInfo(
    val url: String,
    val title: String = "",
    val thumbnail: String = "",
    val duration: Long = 0,
    val uploader: String = "",
    val website: String = "",
    val description: String = "",
    val uploadDate: String = "",
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val formats: List<FormatOption> = emptyList(),
    val audioFormats: List<FormatOption> = emptyList()
)

@Serializable
data class FormatOption(
    val formatId: String,
    val extension: String,
    val resolution: String = "",
    val fps: Int = 0,
    val vcodec: String = "",
    val acodec: String = "",
    val filesize: Long = 0,
    val bitrate: Long = 0,
    val audioBitrate: Int = 0,
    val quality: String = "",
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val isHdr: Boolean = false
)

@Serializable
enum class DownloadType {
    VIDEO,
    AUDIO_ONLY,
    SUBTITLES,
    THUMBNAIL
}
