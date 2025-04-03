package com.tubesmobile.purrytify.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Green,
    secondary = LightGray,
    tertiary = LighterGray,
    background = Black,
    surface = DarkGray,
    onPrimary = Color.White,
    onSecondary = Black,
    onTertiary = Black,
    onBackground = Color.White,
    onSurface = LighterGray,
    surfaceVariant = Maroon,
    onSurfaceVariant = Color.Gray
)

private val LightColorScheme = lightColorScheme(
    primary = Green,
    secondary = LightGray,
    tertiary = LighterGray,
    background = Black,
    surface = DarkGray,
    onPrimary = Color.White,
    onSecondary = Black,
    onTertiary = Black,
    onBackground = Color.White,
    onSurface = LighterGray,
    surfaceVariant = Maroon,
    onSurfaceVariant = Color.Gray
)

@Composable
fun PurrytifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}