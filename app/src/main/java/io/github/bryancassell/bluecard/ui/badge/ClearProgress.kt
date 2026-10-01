package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.ConfirmDialog
import io.github.bryancassell.bluecard.ui.removalButtonColors

/**
 * Clears what the scout recorded ([onClear]) once they confirm, in a dialog with [title] and
 * [message]. Red, and last on its page, so it isn't tapped by mistake.
 */
@Composable
fun ClearProgress(title: String, message: String, onClear: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { confirming = true },
        colors = removalButtonColors(),
        // Lines the button's text up with the page's, as for Add counselor.
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
    ) {
        Text(stringResource(R.string.clear_progress))
    }
    if (confirming) {
        ConfirmDialog(
            title = title,
            message = message,
            confirmLabel = stringResource(R.string.clear_progress_confirm),
            onConfirm = {
                confirming = false
                onClear()
            },
            onDismiss = { confirming = false }
        )
    }
}
