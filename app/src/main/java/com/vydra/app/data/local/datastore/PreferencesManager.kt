package com.vydra.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vydra_settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val THEME_MODE = intPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val ACCENT_COLOR = intPreferencesKey("accent_color")
        private val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
        private val MAX_RETRIES = intPreferencesKey("max_retries")
        private val DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        private val LANGUAGE = stringPreferencesKey("language")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val BACKGROUND_DOWNLOADS = booleanPreferencesKey("background_downloads")
        private val AUTO_UPDATE_YTDLP = booleanPreferencesKey("auto_update_ytdlp")
        private val AUTO_CLEAR_CACHE = booleanPreferencesKey("auto_clear_cache")
    }

    val themeMode: Flow<Int> = dataStore.data.map { it[THEME_MODE] ?: 0 }
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val accentColor: Flow<Int> = dataStore.data.map { it[ACCENT_COLOR] ?: 0 }
    val concurrentDownloads: Flow<Int> = dataStore.data.map { it[CONCURRENT_DOWNLOADS] ?: 3 }
    val maxRetries: Flow<Int> = dataStore.data.map { it[MAX_RETRIES] ?: 3 }
    val downloadDir: Flow<String> = dataStore.data.map { it[DOWNLOAD_DIR] ?: "" }
    val language: Flow<String> = dataStore.data.map { it[LANGUAGE] ?: "en" }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val backgroundDownloads: Flow<Boolean> = dataStore.data.map { it[BACKGROUND_DOWNLOADS] ?: true }
    val autoUpdateYtdlp: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_YTDLP] ?: true }
    val autoClearCache: Flow<Boolean> = dataStore.data.map { it[AUTO_CLEAR_CACHE] ?: false }

    suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAccentColor(color: Int) {
        dataStore.edit { it[ACCENT_COLOR] = color }
    }

    suspend fun setConcurrentDownloads(count: Int) {
        dataStore.edit { it[CONCURRENT_DOWNLOADS] = count }
    }

    suspend fun setMaxRetries(retries: Int) {
        dataStore.edit { it[MAX_RETRIES] = retries }
    }

    suspend fun setDownloadDir(dir: String) {
        dataStore.edit { it[DOWNLOAD_DIR] = dir }
    }

    suspend fun setLanguage(lang: String) {
        dataStore.edit { it[LANGUAGE] = lang }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setBackgroundDownloads(enabled: Boolean) {
        dataStore.edit { it[BACKGROUND_DOWNLOADS] = enabled }
    }

    suspend fun setAutoUpdateYtdlp(enabled: Boolean) {
        dataStore.edit { it[AUTO_UPDATE_YTDLP] = enabled }
    }

    suspend fun setAutoClearCache(enabled: Boolean) {
        dataStore.edit { it[AUTO_CLEAR_CACHE] = enabled }
    }
}
