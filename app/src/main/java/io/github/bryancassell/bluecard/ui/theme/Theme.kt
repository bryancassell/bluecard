package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's theme. It uses [BlueCardLightColorScheme], or [BlueCardDarkColorScheme] in dark mode,
 * on every phone, whatever the wallpaper.
 */
@Composable
fun BlueCardTheme(content: @Composable () -> Unit) {
    val colorScheme =
        if (isSystemInDarkTheme()) BlueCardDarkColorScheme else BlueCardLightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
