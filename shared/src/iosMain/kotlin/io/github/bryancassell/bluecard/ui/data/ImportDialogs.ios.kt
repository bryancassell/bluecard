package io.github.bryancassell.bluecard.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Its content keeps clear of the system bars and the keyboard itself (safeDrawing), so the dialog
// leaves room for neither.
@Composable
internal actual fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            usePlatformInsets = false,
            useSoftwareKeyboardInset = false
        ),
        content = content
    )
}
