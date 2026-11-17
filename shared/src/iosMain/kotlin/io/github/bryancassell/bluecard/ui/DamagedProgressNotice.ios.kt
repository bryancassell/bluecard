package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.damaged_progress_message
import io.github.bryancassell.bluecard.resources.damaged_progress_ok
import io.github.bryancassell.bluecard.resources.damaged_progress_title
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertControllerStyleAlert

/**
 * UIKit's alert, which a tap outside doesn't close. If UIKit won't show it, it shows at the next
 * launch, since only OK marks it seen.
 */
@Composable
actual fun DamagedProgressNotice(onDismiss: () -> Unit) {
    SystemAlert(
        title = stringResource(Res.string.damaged_progress_title),
        message = stringResource(Res.string.damaged_progress_message),
        style = UIAlertControllerStyleAlert,
        buttons = listOf(
            AlertButton(
                stringResource(Res.string.damaged_progress_ok),
                UIAlertActionStyleDefault,
                onDismiss
            )
        ),
        onRefused = {}
    )
}
