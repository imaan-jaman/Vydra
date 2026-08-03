package com.vydra.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vydra.app.data.local.entity.DownloadEntity
import com.vydra.app.domain.model.MediaInfo
import com.vydra.app.domain.repository.DownloadRepository
import com.vydra.app.domain.usecase.AnalyzeUrlUseCase
import com.vydra.app.domain.usecase.StartDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val error: String? = null,
    val showBottomSheet: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyzeUrlUseCase: AnalyzeUrlUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val recentDownloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        viewModelScope.launch {
            startDownloadUseCase(
                mediaInfo = mediaInfo,
                request = com.vydra.app.domain.model.DownloadRequest(
                    url = _uiState.value.url,
                    formatId = formatId,
                    quality = quality
                )
            )
            _uiState.value = _uiState.value.copy(
                showBottomSheet = false,
                url = "",
                mediaInfo = null
            )
        }
    }

    fun dismissBottomSheet() {
        _uiState.value = _uiState.value.copy(showBottomSheet = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
