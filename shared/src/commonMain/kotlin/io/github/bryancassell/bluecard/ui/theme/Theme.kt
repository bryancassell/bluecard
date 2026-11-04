package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.ui.ProvidePressFeedback

/**
 * The app's theme. It uses [BlueCardLightColorScheme], or [BlueCardDarkColorScheme] in dark mode,
 * on every phone, whatever the wallpaper, with the platform's press feedback.
 */
@Composable
fun BlueCardTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) BlueCardDarkColorScheme else BlueCardLightColorScheme
    MaterialTheme(colorScheme = colorScheme) {
        ProvidePressFeedback(darkTheme, content)
    }
}
