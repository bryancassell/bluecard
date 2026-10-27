package io.github.bryancassell.bluecard.ui.data

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
internal actual fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        UseSystemBarIconsForTheme()
        content()
    }
}

/**
 * Gives a full-screen dialog's system bars icons that show on its page: dark on the light page,
 * light on the dark one, as enableEdgeToEdge() chooses for MainActivity's. Android takes the
 * icons from the top full-screen window, which is the dialog's, and that window doesn't get
 * MainActivity's: on Android 8 its navigation bar icons were white on the light page (#299). The
 * window draws no bar backgrounds, so the dialog's own page, its surface color, shows behind the
 * bars.
 */
@Composable
private fun UseSystemBarIconsForTheme() {
    val window = (LocalView.current.parent as DialogWindowProvider).window
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
