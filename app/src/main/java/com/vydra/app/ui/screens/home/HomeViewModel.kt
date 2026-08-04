package com.vydra.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vydra.app.data.local.entity.DownloadEntity
import com.vydra.app.domain.model.MediaInfo
import com.vydra.app.domain.repository.DownloadRepository
import com.vydra.app.domain.usecase.AnalyzeUrlUseCase
import com.vydra.app.domain.usecase.StartDownloadUseCase
import com.vydra.app.engine.UpdateState
import com.vydra.app.engine.YtdlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val error: String? = null,
    val showBottomSheet: Boolean = false,
    val binaryReady: Boolean = false,
    val isInstalling: Boolean = false,
    val installProgress: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyzeUrlUseCase: AnalyzeUrlUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    private val ytdlpEngine: YtdlpEngine,
    downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentDownloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { ytdlpEngine.isReady }
            _uiState.value = _uiState.value.copy(binaryReady = ready)
        }
        observeUpdateState()
    }

    private fun observeUpdateState() {
        viewModelScope.launch {
            ytdlpEngine.updateState.collect { state ->
                when (state) {
                    is UpdateState.Downloading -> {
                        _uiState.value = _uiState.value.copy(
                            isInstalling = true,
                            installProgress = state.progress
                        )
                    }
                    is UpdateState.Success -> {
                        _uiState.value = _uiState.value.copy(
                            binaryReady = true,
                            isInstalling = false
                        )
                    }
                    is UpdateState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isInstalling = false,
                            error = state.message
                        )
                    }
                    is UpdateState.Idle -> {}
                }
            }
        }
    }

    fun installBinary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isInstalling = true)
            ytdlpEngine.updateBinary().onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    error = e.message ?: "Install failed"
                )
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(url = url, error = null)
    }

    fun analyzeUrl() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a URL")
            return
        }

        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { ytdlpEngine.isReady }
            if (!ready) {
                _uiState.value = _uiState.value.copy(error = "Installing yt-dlp... Please wait a moment and try again.")
                installBinary()
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            analyzeUrlUseCase(url)
                .onSuccess { info ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        mediaInfo = info,
                        showBottomSheet = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to analyze URL"
                    )
                }
        }
    }

    fun startDownload(formatId: String, quality: String) {
        val mediaInfo = _uiState.value.mediaInfo ?: return
        val url = _uiState.value.url
        viewModelScope.launch {
            try {
                startDownloadUseCase(
                    mediaInfo = mediaInfo,
                    request = com.vydra.app.domain.model.DownloadRequest(
                        url = url,
                        formatId = formatId,
                        quality = quality
                    )
                )
                _uiState.value = _uiState.value.copy(
                    showBottomSheet = false,
                    url = "",
                    mediaInfo = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showBottomSheet = false,
                    error = e.message ?: "Download failed to start"
                )
            }
        }
    }

    fun dismissBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
