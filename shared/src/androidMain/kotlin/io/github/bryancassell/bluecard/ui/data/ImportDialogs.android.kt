package io.github.bryancassell.bluecard.ui.data

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.confirm_dialog_cancel
import io.github.bryancassell.bluecard.resources.import_choose_message
import io.github.bryancassell.bluecard.resources.import_choose_title
import io.github.bryancassell.bluecard.resources.import_merge
import io.github.bryancassell.bluecard.resources.import_replace_all
import io.github.bryancassell.bluecard.ui.BlueCardTextButton
import io.github.bryancassell.bluecard.ui.removalButtonColors
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun ImportDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.import_choose_title)) },
        text = { Text(stringResource(Res.string.import_choose_message)) },
        confirmButton = {
            BlueCardTextButton(onClick = onMerge) { Text(stringResource(Res.string.import_merge)) }
        },
        // The dialog lays out these buttons, then the confirm button, wrapping them if they
        // don't fit on one line.
        dismissButton = {
            BlueCardTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm_dialog_cancel))
            }
            BlueCardTextButton(onClick = onReplace, colors = removalButtonColors()) {
                Text(stringResource(Res.string.import_replace_all))
            }
        }
    )
}

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
