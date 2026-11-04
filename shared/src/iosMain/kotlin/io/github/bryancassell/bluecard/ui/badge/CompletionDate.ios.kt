package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.bryancassell.bluecard.ui.rememberStringsLocale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toNSTimeZone

/**
 * iOS's own calendar, in a sheet from the bottom of the window ([DatePickerSheet]), in the
 * strings' locale, as the page's dates are, and in the time zone the app's today is in
 * (`DeviceClock`). The sheet shows while this is composed, so it closes once the page stops
 * asking. The keyboard goes down first, as Android's does when its dialog opens, since it would
 * cover the half-height calendar's last weeks. It comes back once the sheet closes.
 */
@Composable
internal actual fun CompletionDatePicker(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val viewController = LocalUIViewController.current
    val keyboard = LocalSoftwareKeyboardController.current
    val locale = rememberStringsLocale()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(viewController) {
        keyboard?.hide()
        val sheet = DatePickerSheet(
            initial = initial,
            today = today,
            locale = locale,
            timeZone = TimeZone.currentSystemDefault().toNSTimeZone(),
            onConfirm = { currentOnConfirm(it) },
            onCancel = { currentOnDismiss() }
        )
        sheet.show(over = viewController)
        onDispose(sheet::close)
    }
}
