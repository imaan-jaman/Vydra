package com.vydra.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vydra.app.data.local.datastore.PreferencesManager

private val LightColorScheme = lightColorScheme(
    primary = VydraBlue,
    onPrimary = Color.White,
    primaryContainer = VydraBlueLight,
    onPrimaryContainer = VydraBlueDark,
    secondary = VydraTeal,
    onSecondary = Color.White,
    secondaryContainer = VydraTealLight,
    onSecondaryContainer = VydraTealDark,
    tertiary = VydraPurple,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = VydraBlueLight,
    onPrimary = VydraBlueDark,
    primaryContainer = VydraBlue,
    onPrimaryContainer = VydraBlueLight,
    secondary = VydraTealLight,
    onSecondary = VydraTealDark,
    secondaryContainer = VydraTeal,
    onSecondaryContainer = VydraTealLight,
    tertiary = VydraPurple,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorRedLight,
    onError = Color.Black
)

private val AMOLEDColorScheme = darkColorScheme(
    primary = VydraBlueLight,
    onPrimary = VydraBlueDark,
    primaryContainer = VydraBlue,
    onPrimaryContainer = VydraBlueLight,
    secondary = VydraTealLight,
    onSecondary = VydraTealDark,
    secondaryContainer = VydraTeal,
    onSecondaryContainer = VydraTealLight,
    tertiary = VydraPurple,
    background = SurfaceAMOLED,
    onBackground = OnSurfaceDark,
    surface = SurfaceAMOLED,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF1A1A1A),
    error = ErrorRedLight,
    onError = Color.Black
)

@Composable
fun VydraTheme(
    preferencesManager: PreferencesManager,
    content: @Composable () -> Unit
) {
    val themeMode by preferencesManager.themeMode.collectAsState(initial = 0)
    val dynamicColor by preferencesManager.dynamicColor.collectAsState(initial = true)
    val isDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        2 -> AMOLEDColorScheme
        1 -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                dynamicDarkColorScheme(context)
            } else {
                DarkColorScheme
            }
        }
        else -> {
            if (isDark) {
                if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val context = LocalContext.current
                    dynamicDarkColorScheme(context)
                } else {
                    DarkColorScheme
                }
            } else {
                if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val context = LocalContext.current
                    dynamicLightColorScheme(context)
                } else {
                    LightColorScheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VydraTypography,
        content = content
    )
}
