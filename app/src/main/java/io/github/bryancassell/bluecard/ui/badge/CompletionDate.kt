package io.github.bryancassell.bluecard.ui.badge

import android.text.TextUtils
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.text.completionDateFormatter
import io.github.bryancassell.bluecard.ui.ButtonText
import io.github.bryancassell.bluecard.ui.stringsLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formats a date something was done on, such as a requirement's completion date: "Apr 15,
 * 2026", in the language of the strings around it, with that language's digits
 * (ARCHITECTURE.md, Language and layout direction).
 */
@Composable
fun rememberCompletionDateFormatter(): DateTimeFormatter {
    val locale = stringsLocale()
    return remember(locale) { completionDateFormatter(locale) }
}

/**
 * [text] about a date something was done on, such as "Completed on Apr 15, 2026", with buttons
 * to pick the date or remove it, labeled [removeText]. Without a date, the picker opens at
 * [suggested], if given, or else at [today]. Dates after [today] can't be picked. It's read as the
 * picker opens, so a page left open past midnight offers the new day. Screen readers
 * read the date's [label], if it has one, after each button's text, such as "Add date: Week
 * starting": moving from control to control, they don't hear a label shown above the date. The
 * text is in [textStyle].
 */
@Composable
fun EditableDate(
    text: String,
    date: LocalDate?,
    today: () -> LocalDate,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    @StringRes removeText: Int = R.string.requirement_remove_date,
    suggested: LocalDate? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Column(modifier) {
        Text(
            text = text,
            style = textStyle,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // Lines the buttons' text up with the date's.
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            val pickText = if (date == null) {
                R.string.requirement_add_date
            } else {
                R.string.requirement_change_date
            }
            PickDate(date ?: suggested, today, onDateChange) { onClick ->
                DateButton(stringResource(pickText), label, onClick)
            }
            if (date != null) {
                DateButton(stringResource(removeText), label) { onDateChange(null) }
            }
        }
    }
}

/**
 * A text button labeled [text] for a date, which screen readers read with the date's [label] after
 * it, if it has one.
 */
@Composable
private fun DateButton(text: String, label: String?, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        ButtonText(
            text = text,
            description = label?.let { stringResource(R.string.labeled_date_button, text, it) }
        )
    }
}

/**
 * Shows the [button] that asks for a date something was done on, and the picker while it's
 * asking. The picker starts at [initial], or at [today] without one, and gives [onPick] the date
 * picked. Dates after [today] can't be picked. It's read as the picker opens, so a page left open
 * past midnight offers the new day.
 */
@Composable
internal fun PickDate(
    initial: LocalDate?,
    today: () -> LocalDate,
    onPick: (LocalDate) -> Unit,
    button: @Composable (onClick: () -> Unit) -> Unit
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    button { picking = true }
    if (picking) {
        val latest = remember { today() }
        CompletionDatePickerDialog(
            initial = initial ?: latest,
            today = latest,
            onConfirm = {
                picking = false
                onPick(it)
            },
            onDismiss = { picking = false }
        )
    }
}

/**
 * Asks for the date something was done on, such as when a requirement was completed, starting
 * at [initial]. Dates after [today] can't be picked or confirmed, so it starts at [today]
 * instead of a later [initial], such as a date recorded while the device's clock was ahead,
 * which Material 3 would otherwise show selected. The picker itself follows the device's
 * language, like other Material labels, and is laid out in that language's direction, so a
 * Persian calendar reads right-to-left: laid out left-to-right, as the app's screens are, its
 * dates would read out of order.
 */
@Composable
fun CompletionDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.coerceAtMost(today).toPickerMillis(),
        selectableDates = remember(today) { NotAfter(today) }
    )
    CompositionLocalProvider(LocalLayoutDirection provides pickerLayoutDirection()) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                // Null while a typed date isn't valid. One the picker doesn't offer can still
                // be selected when it's restored with an earlier today, as when the system
                // stopped the app and the scout then crossed a date line westward.
                val selected = state.selectedDateMillis
                    ?.takeIf(state.selectableDates::isSelectableDate)
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

/** Dates up to [today]: nothing the scout records can be done in the future. */
private class NotAfter(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toPickerDate() <= today

    override fun isSelectableYear(year: Int) = year <= today.year
}

// The date picker gives dates as the start of their day in UTC, in milliseconds.

private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
