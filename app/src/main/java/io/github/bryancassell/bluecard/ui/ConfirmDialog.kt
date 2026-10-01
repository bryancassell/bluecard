package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R

/**
 * Asks the scout to confirm removing something they recorded, which can't be undone. The
 * confirm button, labeled [confirmLabel], is red, the theme's error color, so it stands apart
 * from Cancel. The caller closes the dialog: [onConfirm] and [onDismiss] (Cancel, Back or a
 * tap outside) are each called once per tap.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = removalButtonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm_dialog_cancel))
            }
        }
    )
}

/**
 * Colors of a text button that removes what the scout recorded, or asks to: red, the theme's
 * error color.
 */
@Composable
fun removalButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
