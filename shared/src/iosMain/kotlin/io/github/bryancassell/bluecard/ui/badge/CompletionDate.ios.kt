package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.strings_language
import io.github.bryancassell.bluecard.text.currentRegion
import io.github.bryancassell.bluecard.text.stringsLocale
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * iOS's own calendar, in a sheet from the bottom of the window ([DatePickerSheet]), in the
 * strings' locale ([stringsLocale]), as the page's dates are. The sheet shows while this is
 * composed, so it closes once the page stops asking.
 */
@Composable
internal actual fun CompletionDatePicker(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val viewController = LocalUIViewController.current
    val stringsLanguage = stringResource(Res.string.strings_language)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(viewController) {
        val sheet = DatePickerSheet(
            initial = initial,
            today = today,
            locale = stringsLocale(stringsLanguage, currentRegion()),
            onConfirm = { currentOnConfirm(it) },
            onCancel = { currentOnDismiss() }
        )
        sheet.show(over = viewController)
        onDispose(sheet::close)
    }
}
