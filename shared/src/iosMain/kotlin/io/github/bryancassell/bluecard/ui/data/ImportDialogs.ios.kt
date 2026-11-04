package io.github.bryancassell.bluecard.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.confirm_dialog_cancel
import io.github.bryancassell.bluecard.resources.import_choose_message
import io.github.bryancassell.bluecard.resources.import_choose_title
import io.github.bryancassell.bluecard.resources.import_merge
import io.github.bryancassell.bluecard.resources.import_replace_all
import io.github.bryancassell.bluecard.ui.AlertButton
import io.github.bryancassell.bluecard.ui.SystemAlert
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertControllerStyleActionSheet

/**
 * UIKit's action sheet, as `ConfirmDialog` is on iOS, since the choice follows the scout's choosing
 * a file. Replace all has UIKit's destructive style, which draws it red, and comes first, where
 * Apple's guidelines put a destructive choice "where they tend to be most noticeable"
 * (https://developer.apple.com/design/human-interface-guidelines/action-sheets). Then Merge, then
 * Cancel. If UIKit won't show the sheet, it's as if the scout cancelled, so they can import again.
 */
@Composable
actual fun ImportDialog(onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    SystemAlert(
        title = stringResource(Res.string.import_choose_title),
        message = stringResource(Res.string.import_choose_message),
        style = UIAlertControllerStyleActionSheet,
        buttons = listOf(
            AlertButton(
                stringResource(Res.string.import_replace_all),
                UIAlertActionStyleDestructive,
                onReplace
            ),
            AlertButton(
                stringResource(Res.string.import_merge),
                UIAlertActionStyleDefault,
                onMerge
            ),
            AlertButton(
                stringResource(Res.string.confirm_dialog_cancel),
                UIAlertActionStyleCancel,
                onDismiss
            )
        ),
        onRefused = onDismiss
    )
}

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
