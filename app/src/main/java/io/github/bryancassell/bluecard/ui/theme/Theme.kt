package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's theme. It uses [BlueCardColorScheme] on every phone, whatever the wallpaper, and in
 * dark mode too, until the app has a dark scheme.
 */
@Composable
fun BlueCardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlueCardColorScheme, content = content)
}
