package io.github.bryancassell.bluecard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R

/**
 * Asks before Back discards the page's unsaved changes. While the fields have [changed], Back
 * opens a [ConfirmDialog] with [message], whose red Discard button calls [onDiscard] to close
 * the page. Otherwise Back closes the page as usual.
 *
 * The handler is on only while there are changes, like the predictive back guide's form, whose
 * "Are you sure..." handler "is enabled when the user enters data into a form, and disabled
 * otherwise": https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
 */
@Composable
fun ConfirmDiscardOnBack(
    changed: Boolean,
    onDiscard: () -> Unit,
    message: String = stringResource(R.string.discard_changes_message)
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = changed) { confirming = true }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            message = message,
            confirmLabel = stringResource(R.string.discard_changes_confirm),
            onConfirm = {
                confirming = false
                onDiscard()
            },
            onDismiss = { confirming = false }
        )
    }
}
