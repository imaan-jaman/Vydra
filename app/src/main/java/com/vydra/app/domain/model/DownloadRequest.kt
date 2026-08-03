package com.vydra.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadRequest(
    val url: String,
    val downloadType: DownloadType = DownloadType.VIDEO,
    val formatId: String? = null,
    val quality: String = "best",
    val audioBitrate: String = "192",
    val container: String = "mp4",
    val customFileName: String? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = true,
    val outputDir: String? = null,
    val subtitleLang: String? = null
)
