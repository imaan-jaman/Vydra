package com.vydra.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vydra.app.data.local.entity.DownloadEntity
import com.vydra.app.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val history: StateFlow<List<DownloadEntity>> = combine(
        _searchQuery,
        downloadRepository.getAllDownloads()
    ) { query, downloads ->
        if (query.isBlank()) downloads
        else downloads.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true) ||
                    it.uploader.contains(query, ignoreCase = true) ||
                    it.website.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(download)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            downloadRepository.clearAll()
        }
    }
}
