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
 * A scout's records may cover years of work, so they're never lost without the scout knowing
 * (#70). It doesn't suggest restoring Android's backup: that means reinstalling BlueCard, which
 * deletes what's on the phone, and works only if the nightly backup ran before the damage and
 * hasn't run since.
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
