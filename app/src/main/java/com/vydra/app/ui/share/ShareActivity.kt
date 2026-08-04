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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vydra.app.domain.model.MediaInfo
import com.vydra.app.domain.model.DownloadRequest
import com.vydra.app.domain.model.DownloadType
import com.vydra.app.domain.usecase.StartDownloadUseCase
import com.vydra.app.engine.YtdlpEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject
    lateinit var ytdlpEngine: YtdlpEngine

    @Inject
    lateinit var startDownloadUseCase: StartDownloadUseCase

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
            MaterialTheme {
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
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
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
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                when (val currentState = state) {
                    is ShareUiState.Analyzing -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
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
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentState.info.uploader.ifEmpty { currentState.info.website },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            startDownloadUseCase(
                                                mediaInfo = currentState.info,
                                                request = DownloadRequest(
                                                    url = url,
                                                    downloadType = DownloadType.VIDEO,
                                                    quality = "best"
                                                )
                                            )
                                            onDownloadStarted()
                                        } catch (e: Exception) {
                                            state = ShareUiState.Error(e.message ?: "Failed to start download")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download")
                            }
                        }
                    }

                    is ShareUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
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
