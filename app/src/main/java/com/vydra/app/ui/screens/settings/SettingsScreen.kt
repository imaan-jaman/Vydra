package com.vydra.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val concurrentDownloads by viewModel.concurrentDownloads.collectAsState()
    val maxRetries by viewModel.maxRetries.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val backgroundDownloads by viewModel.backgroundDownloads.collectAsState()
    val autoUpdateYtdlp by viewModel.autoUpdateYtdlp.collectAsState()
    val autoClearCache by viewModel.autoClearCache.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection("Appearance") {
                SettingsOption(
                    icon = Icons.Default.Brightness6,
                    title = "Theme",
                    subtitle = when (themeMode) {
                        0 -> "System default"
                        1 -> "Dark"
                        2 -> "AMOLED Black"
                        else -> "System default"
                    },
                    onClick = {
                        viewModel.setThemeMode((themeMode + 1) % 3)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchOption(
                    icon = Icons.Default.ColorLens,
                    title = "Dynamic Color",
                    subtitle = "Use Material You colors",
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("Downloads") {
                SliderOption(
                    icon = Icons.Default.Download,
                    title = "Concurrent Downloads",
                    value = concurrentDownloads.toFloat(),
                    valueRange = 1f..5f,
                    onValueChange = { viewModel.setConcurrentDownloads(it.toInt()) },
                    displayValue = "$concurrentDownloads"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SliderOption(
                    icon = Icons.Default.Repeat,
                    title = "Max Retries",
                    value = maxRetries.toFloat(),
                    valueRange = 0f..10f,
                    onValueChange = { viewModel.setMaxRetries(it.toInt()) },
                    displayValue = "$maxRetries"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsOption(
                    icon = Icons.Default.Folder,
                    title = "Download Location",
                    subtitle = "Default download directory",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("Engine") {
                SwitchOption(
                    icon = Icons.Default.Refresh,
                    title = "Auto Update yt-dlp",
                    subtitle = "Keep yt-dlp up to date",
                    checked = autoUpdateYtdlp,
                    onCheckedChange = viewModel::setAutoUpdateYtdlp
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchOption(
                    icon = Icons.Default.Delete,
                    title = "Auto Clear Cache",
                    subtitle = "Clear cache after download",
                    checked = autoClearCache,
                    onCheckedChange = viewModel::setAutoClearCache
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("Notifications") {
                SwitchOption(
                    icon = Icons.Default.Star,
                    title = "Notifications",
                    subtitle = "Show download notifications",
                    checked = notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchOption(
                    icon = Icons.Default.FastForward,
                    title = "Background Downloads",
                    subtitle = "Allow downloads in background",
                    checked = backgroundDownloads,
                    onCheckedChange = viewModel::setBackgroundDownloads
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSection("About") {
                SettingsOption(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun SliderOption(
    icon: ImageVector,
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = (valueRange.endInclusive - valueRange.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}


