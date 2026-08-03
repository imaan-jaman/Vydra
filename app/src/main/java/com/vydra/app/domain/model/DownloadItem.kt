package com.vydra.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadItem(
    val id: Long = 0,
    val url: String,
    val title: String = "",
    val thumbnail: String = "",
    val duration: Long = 0,
    val uploader: String = "",
    val website: String = "",
    val filePath: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val downloadedSize: Long = 0,
    val progress: Float = 0f,
    val speed: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val format: String = "",
    val quality: String = "",
    val mimeType: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    val errorMessage: String? = null,
    val taskId: String? = null
)

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
