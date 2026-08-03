package com.vydra.app.ui.screens.search

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
import javax.inject.Inject

data class SearchFilter(
    val type: String = "all",
    val status: String = "all"
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    downloadRepository: DownloadRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    val searchResults: StateFlow<List<DownloadEntity>> = combine(
        _searchQuery,
        _filter,
        downloadRepository.getAllDownloads()
    ) { query, filter, downloads ->
        downloads.filter { download ->
            val matchesQuery = query.isBlank() ||
                    download.title.contains(query, ignoreCase = true) ||
                    download.url.contains(query, ignoreCase = true) ||
                    download.uploader.contains(query, ignoreCase = true) ||
                    download.website.contains(query, ignoreCase = true)

            val matchesType = when (filter.type) {
                "video" -> download.format in listOf("mp4", "webm", "mkv", "avi", "mov") ||
                        download.mimeType.startsWith("video")
                "audio" -> download.format in listOf("mp3", "m4a", "aac", "flac", "ogg", "wav") ||
                        download.mimeType.startsWith("audio")
                else -> true
            }

            val matchesStatus = when (filter.status) {
                "completed" -> download.status == "COMPLETED"
                "failed" -> download.status == "FAILED"
                "queued" -> download.status == "QUEUED"
                "active" -> download.status == "DOWNLOADING"
                else -> true
            }

            matchesQuery && matchesType && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTypeFilterChanged(type: String) {
        _filter.value = _filter.value.copy(type = type)
    }

    fun onStatusFilterChanged(status: String) {
        _filter.value = _filter.value.copy(status = status)
    }
}
