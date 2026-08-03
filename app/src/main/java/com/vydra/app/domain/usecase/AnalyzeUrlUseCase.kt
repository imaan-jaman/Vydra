package com.vydra.app.domain.usecase

import com.vydra.app.domain.model.MediaInfo
import com.vydra.app.engine.YtdlpEngine
import javax.inject.Inject

class AnalyzeUrlUseCase @Inject constructor(
    private val ytdlpEngine: YtdlpEngine
) {
    suspend operator fun invoke(url: String): Result<MediaInfo> {
        return ytdlpEngine.getMediaInfo(url)
    }
}
