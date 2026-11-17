package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.damaged_progress_message
import io.github.bryancassell.bluecard.resources.damaged_progress_ok
import io.github.bryancassell.bluecard.resources.damaged_progress_title
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun DamagedProgressNotice(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.damaged_progress_title)) },
        text = { Text(stringResource(Res.string.damaged_progress_message)) },
        confirmButton = {
            BlueCardTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.damaged_progress_ok))
            }
        }
    )
}
