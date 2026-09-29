package io.github.bryancassell.bluecard.ui.badge

import android.text.TextUtils
import android.view.View
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.stringsLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle

/**
 * Formats a completion date, such as "Apr 15, 2026", in the language of the strings around
 * it, with that language's digits (ARCHITECTURE.md, UI layer).
 */
@Composable
fun rememberCompletionDateFormatter(): DateTimeFormatter {
    val locale = stringsLocale()
    return remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withDecimalStyle(DecimalStyle.of(locale))
    }
}

/**
 * Asks for the date a requirement was completed on, starting at [initial]. Dates after [today]
 * can't be picked. The picker itself follows the device's language, like other Material labels,
 * and is laid out in that language's direction, so a Persian calendar reads right-to-left
 * (ARCHITECTURE.md, UI layer).
 */
@Composable
fun CompletionDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        selectableDates = remember(today) { NotAfter(today) }
    )
    CompositionLocalProvider(LocalLayoutDirection provides pickerLayoutDirection()) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                // Null while a typed date isn't valid.
                val selected = state.selectedDateMillis
                TextButton(
                    onClick = { selected?.let { onConfirm(it.toPickerDate()) } },
                    enabled = selected != null
                ) {
                    Text(stringResource(R.string.date_picker_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.date_picker_cancel))
                }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * The direction of the language the picker shows its labels and dates in: the device's first
 * locale, which Material 3 reads from the configuration. The rest of the app is laid out in the
 * strings' language's direction instead.
 */
@Composable
@ReadOnlyComposable
private fun pickerLayoutDirection(): LayoutDirection =
    when (TextUtils.getLayoutDirectionFromLocale(LocalConfiguration.current.locales[0])) {
        View.LAYOUT_DIRECTION_RTL -> LayoutDirection.Rtl
        else -> LayoutDirection.Ltr
    }

/** Dates up to [today]: a requirement can't be completed in the future. */
private class NotAfter(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toPickerDate() <= today

    override fun isSelectableYear(year: Int) = year <= today.year
}

// The date picker gives dates as the start of their day in UTC, in milliseconds.

private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
