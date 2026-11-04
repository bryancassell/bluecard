package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.confirm_dialog_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Material's dialog, with the confirm button in the theme's error color.
 *
 * It ignores taps, and Back, for the double-tap timeout as it opens, as the merge dialog does
 * (ARCHITECTURE.md, Double taps). Otherwise the second tap of a double tap on a button that opens
 * it, such as Clear progress, could land outside the dialog and close it again.
 */
@Composable
actual fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val opening = isOpeningForDoubleTap()
    AlertDialog(
        onDismissRequest = { if (!opening) onDismiss() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            BlueCardTextButton(onClick = {
                if (!opening) onConfirm()
            }, colors = removalButtonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            BlueCardTextButton(onClick = { if (!opening) onDismiss() }) {
                Text(stringResource(Res.string.confirm_dialog_cancel))
            }
        }
    )
}
