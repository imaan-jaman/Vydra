package com.vydra.app.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vydra.app.data.local.datastore.PreferencesManager
import com.vydra.app.domain.model.DownloadRequest
import com.vydra.app.domain.model.DownloadType
import com.vydra.app.domain.model.MediaInfo
import com.vydra.app.domain.usecase.StartDownloadUseCase
import com.vydra.app.engine.YtdlpEngine
import com.vydra.app.ui.components.hapticAction
import com.vydra.app.ui.theme.VydraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject
    lateinit var ytdlpEngine: YtdlpEngine

    @Inject
    lateinit var startDownloadUseCase: StartDownloadUseCase

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            }
            else -> ""
        }

        if (sharedText.isBlank()) {
            finish()
            return
        }

        val url = extractUrl(sharedText)
        if (url.isBlank()) {
            finish()
            return
        }

        setContent {
            VydraTheme(preferencesManager = preferencesManager) {
                ShareDownloadDialog(
                    url = url,
                    ytdlpEngine = ytdlpEngine,
                    startDownloadUseCase = startDownloadUseCase,
                    onDismiss = { finish() },
                    onDownloadStarted = { finish() }
                )
            }
        }
    }

    private fun extractUrl(text: String): String {
        val urlPattern = Regex("(https?://[^\\s]+)")
        val match = urlPattern.find(text)?.value ?: text.trim()
        return match.trimEnd('.', ',', ';', '!', '?', ')', ']', '"', '\'')
    }
}

data class QualityPreset(
    val label: String,
    val quality: String,
    val isAudio: Boolean = false
)

private val videoPresets = listOf(
    QualityPreset("4K", "4k"),
    QualityPreset("1080p", "1080"),
    QualityPreset("720p", "720"),
    QualityPreset("480p", "480"),
    QualityPreset("360p", "360")
)

private val audioPresets = listOf(
    QualityPreset("MP3", "mp3", isAudio = true),
    QualityPreset("M4A", "m4a", isAudio = true)
)

@Composable
fun ShareDownloadDialog(
    url: String,
    ytdlpEngine: YtdlpEngine,
    startDownloadUseCase: StartDownloadUseCase,
    onDismiss: () -> Unit,
    onDownloadStarted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShareUiState>(ShareUiState.Analyzing) }
    var selectedType by remember { mutableStateOf("video") }
    var selectedQuality by remember { mutableStateOf(videoPresets[1]) }

    LaunchedEffect(selectedType) {
        selectedQuality = if (selectedType == "video") videoPresets[1] else audioPresets[0]
    }

    LaunchedEffect(url) {
        ytdlpEngine.ensureBinary()
            .onSuccess {
                ytdlpEngine.getMediaInfo(url)
                    .onSuccess { info ->
                        state = ShareUiState.Ready(info)
                    }
                    .onFailure { e ->
                        state = ShareUiState.Error(e.message ?: "Failed to analyze link")
                    }
            }
            .onFailure { e ->
                state = ShareUiState.Error(e.message ?: "Failed to install yt-dlp")
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download with Vydra",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = hapticAction(onDismiss),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (val currentState = state) {
                    is ShareUiState.Analyzing -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Analyzing link...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is ShareUiState.Ready -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = currentState.info.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentState.info.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentState.info.uploader.ifEmpty { currentState.info.website },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Download Type",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedType == "video",
                                onClick = hapticAction { selectedType = "video" },
                                label = { Text("Video") },
                                leadingIcon = {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = selectedType == "audio",
                                onClick = hapticAction { selectedType = "audio" },
                                label = { Text("Audio") },
                                leadingIcon = {
                                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        Text(
                            text = "Quality",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val presets = if (selectedType == "video") videoPresets else audioPresets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = selectedQuality.label == preset.label,
                                    onClick = hapticAction { selectedQuality = preset },
                                    label = {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = hapticAction(onDismiss),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = hapticAction {
                                    scope.launch {
                                        try {
                                            startDownloadUseCase(
                                                mediaInfo = currentState.info,
                                                request = DownloadRequest(
                                                    url = url,
                                                    downloadType = if (selectedType == "audio") DownloadType.AUDIO_ONLY else DownloadType.VIDEO,
                                                    quality = selectedQuality.quality
                                                )
                                            )
                                            onDownloadStarted()
                                        } catch (e: Exception) {
                                            state = ShareUiState.Error(e.message ?: "Failed to start download")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is ShareUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class ShareUiState {
    data object Analyzing : ShareUiState()
    data class Ready(val info: MediaInfo) : ShareUiState()
    data class Error(val message: String) : ShareUiState()
}
