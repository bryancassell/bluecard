package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.confirm_dialog_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Asks the scout to confirm removing something they recorded or typed, which can't be undone.
 * Every removal asks with it, so they're all alike. The confirm button, labeled [confirmLabel],
 * is red, the theme's error color, so it stands apart from Cancel. The caller closes the
 * dialog: [onConfirm] and [onDismiss] (Cancel, Back or a tap outside) are each called once per
 * tap.
 *
 * It ignores taps, and Back, for the double-tap timeout as it opens, as the merge dialog does
 * (ARCHITECTURE.md, Double taps). Otherwise the second tap of a double tap on iOS's Back button,
 * which opens "Discard changes?" on a page with unsaved changes, landed outside the dialog and
 * closed it again, and a double tap on a button such as Clear progress could do the same.
 */
@Composable
fun ConfirmDialog(
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
            TextButton(onClick = { if (!opening) onConfirm() }, colors = removalButtonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!opening) onDismiss() }) {
                Text(stringResource(Res.string.confirm_dialog_cancel))
            }
        }
    )
}

/**
 * Colors of a text button that removes what the scout recorded, or opens the dialog that asks
 * to: red, the theme's error color.
 */
@Composable
fun removalButtonColors(): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)

/**
 * Colors of an outlined button that removes what the scout recorded, or opens the dialog that
 * asks to, where the buttons beside it are outlined: red text, as [removalButtonColors] gives a
 * text button. Outlined and text buttons have different default colors, so each needs its own.
 */
@Composable
fun removalOutlinedButtonColors(): ButtonColors =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
