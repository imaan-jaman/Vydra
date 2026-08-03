package com.vydra.app.ui.screens.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vydra.app.data.local.entity.DownloadEntity
import com.vydra.app.domain.repository.DownloadRepository
import com.vydra.app.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val selectedTab: Int = 0
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val activeDownloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .getDownloadsByStatus("DOWNLOADING")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedDownloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .getDownloadsByStatus("QUEUED")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .getDownloadsByStatus("COMPLETED")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val failedDownloads: StateFlow<List<DownloadEntity>> = downloadRepository
        .getDownloadsByStatus("FAILED")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun pauseDownload(download: DownloadEntity) {
        DownloadService.pauseDownload(context, download.id)
    }

    fun cancelDownload(download: DownloadEntity) {
        DownloadService.cancelDownload(context, download.id)
    }

    fun retryDownload(download: DownloadEntity) {
        DownloadService.startDownload(context, download.id, download.url)
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(download)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            downloadRepository.clearCompleted()
        }
    }
}
