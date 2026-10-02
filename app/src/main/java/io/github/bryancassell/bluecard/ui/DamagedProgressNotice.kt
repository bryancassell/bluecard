package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R

/**
 * Tells the scout that the progress saved on the phone was damaged, so BlueCard started over
 * without it. Only OK closes it ([onDismiss]); Back and taps outside don't, so it isn't missed.
 */
@Composable
fun DamagedProgressNotice(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.damaged_progress_title)) },
        text = { Text(stringResource(R.string.damaged_progress_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.damaged_progress_ok)) }
        }
    )
}
