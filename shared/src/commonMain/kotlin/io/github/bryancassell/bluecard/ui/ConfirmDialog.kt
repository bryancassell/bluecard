package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Asks the scout to confirm removing something they recorded or typed, which can't be undone.
 * Every removal asks with it, so they're all alike. The confirm button, labeled [confirmLabel],
 * is red, so it stands apart from Cancel. The caller closes the dialog: [onConfirm] and
 * [onDismiss] (Cancel, Back or a tap outside) are each called once per tap. It's Material's
 * dialog on Android, and UIKit's action sheet on iOS.
 */
@Composable
expect fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)

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
