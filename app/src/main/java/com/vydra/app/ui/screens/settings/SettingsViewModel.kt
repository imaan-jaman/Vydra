package com.vydra.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vydra.app.data.local.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val themeMode = preferencesManager.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    val dynamicColor = preferencesManager.dynamicColor.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val concurrentDownloads = preferencesManager.concurrentDownloads.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 3
    )

    val maxRetries = preferencesManager.maxRetries.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 3
    )

    val notificationsEnabled = preferencesManager.notificationsEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val backgroundDownloads = preferencesManager.backgroundDownloads.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val autoUpdateYtdlp = preferencesManager.autoUpdateYtdlp.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )

    val autoClearCache = preferencesManager.autoClearCache.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDynamicColor(enabled) }
    }

    fun setConcurrentDownloads(count: Int) {
        viewModelScope.launch { preferencesManager.setConcurrentDownloads(count) }
    }

    fun setMaxRetries(retries: Int) {
        viewModelScope.launch { preferencesManager.setMaxRetries(retries) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) }
    }

    fun setBackgroundDownloads(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setBackgroundDownloads(enabled) }
    }

    fun setAutoUpdateYtdlp(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoUpdateYtdlp(enabled) }
    }

    fun setAutoClearCache(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoClearCache(enabled) }
    }
}
