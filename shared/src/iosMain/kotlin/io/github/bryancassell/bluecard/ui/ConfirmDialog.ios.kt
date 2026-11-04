package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.confirm_dialog_cancel
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertControllerStyleActionSheet

/**
 * UIKit's action sheet, which Apple's guidelines ask for in place of an alert "to offer choices
 * related to an intentional action", as each removal follows a tap on Delete, Clear progress or
 * Back (https://developer.apple.com/design/human-interface-guidelines/action-sheets). The confirm
 * button has UIKit's destructive style, which draws it red, and Cancel is last. If UIKit won't
 * show the sheet, it's as if the scout cancelled, so the page can ask again.
 */
@Composable
actual fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    SystemAlert(
        title = title,
        message = message,
        style = UIAlertControllerStyleActionSheet,
        buttons = listOf(
            AlertButton(confirmLabel, UIAlertActionStyleDestructive, onConfirm),
            AlertButton(
                stringResource(Res.string.confirm_dialog_cancel),
                UIAlertActionStyleCancel,
                onDismiss
            )
        ),
        onRefused = onDismiss
    )
}
