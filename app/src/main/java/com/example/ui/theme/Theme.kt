package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PolishPrimaryDark,
    secondary = PolishOnPrimaryContainerDark,
    background = PolishBackgroundDark,
    surface = PolishSurfaceDark,
    onPrimary = PolishBackgroundDark,
    onSecondary = PolishOnBackgroundDark,
    onBackground = PolishOnBackgroundDark,
    onSurface = PolishOnBackgroundDark,
    primaryContainer = PolishPrimaryContainerDark,
    onPrimaryContainer = PolishOnPrimaryContainerDark,
    secondaryContainer = PolishSecondaryContainerDark,
    onSecondaryContainer = PolishOnPrimaryContainerDark,
    surfaceVariant = PolishSurfaceVariantDark,
    onSurfaceVariant = PolishOnSurfaceVariantDark,
    outlineVariant = PolishOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = PolishPrimaryLight,
    secondary = PolishSecondaryLight,
    background = PolishBackgroundLight,
    surface = PolishBackgroundLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PolishOnBackgroundLight,
    onSurface = PolishOnBackgroundLight,
    primaryContainer = PolishPrimaryContainerLight,
    onPrimaryContainer = PolishOnPrimaryContainerLight,
    secondaryContainer = PolishSecondaryContainerLight,
    onSecondaryContainer = PolishOnPrimaryContainerLight,
    surfaceVariant = PolishSurfaceVariantLight,
    onSurfaceVariant = PolishOnSurfaceVariantLight,
    outlineVariant = PolishOutlineVariantLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamicColor option, but respect spreadsheet primary colors as fallback/core representation
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
