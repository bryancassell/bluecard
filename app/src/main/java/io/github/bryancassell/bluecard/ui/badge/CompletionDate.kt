package io.github.bryancassell.bluecard.ui.badge

import android.text.TextUtils
import android.view.View
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.text.completionDateFormatter
import io.github.bryancassell.bluecard.ui.stringsLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formats a date something was done on, such as a requirement's completion date: "Apr 15,
 * 2026", in the language of the strings around it, with that language's digits
 * (ARCHITECTURE.md, UI layer).
 */
@Composable
fun rememberCompletionDateFormatter(): DateTimeFormatter {
    val locale = stringsLocale()
    return remember(locale) { completionDateFormatter(locale) }
}

/**
 * [text] about a date something was done on, such as "Completed on Apr 15, 2026", with buttons
 * to pick the date or remove it. Dates after [today] can't be picked. Screen readers read the
 * date's [label], if it has one, with each button, such as "Start: Add date", so the buttons of
 * a page with more than one date aren't all the same to them.
 */
@Composable
fun EditableDate(
    text: String,
    date: LocalDate?,
    today: LocalDate,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        // Lines the buttons' text up with the date's.
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            val pickText = if (date == null) {
                R.string.requirement_add_date
            } else {
                R.string.requirement_change_date
            }
            val pick = stringResource(pickText)
            TextButton(
                onClick = { picking = true },
                modifier = Modifier.readAs(
                    label?.let { stringResource(R.string.labeled_date_button, it, pick) }
                )
            ) {
                Text(pick)
            }
            if (date != null) {
                val remove = stringResource(R.string.requirement_remove_date)
                TextButton(
                    onClick = { onDateChange(null) },
                    modifier = Modifier.readAs(
                        label?.let { stringResource(R.string.labeled_date_button, it, remove) }
                    )
                ) {
                    Text(remove)
                }
            }
        }
    }
    if (picking) {
        CompletionDatePickerDialog(
            initial = date ?: today,
            today = today,
            onConfirm = {
                picking = false
                onDateChange(it)
            },
            onDismiss = { picking = false }
        )
    }
}

/** Has screen readers read [description] in place of the element's text, unless it's null. */
private fun Modifier.readAs(description: String?): Modifier =
    if (description == null) this else semantics { contentDescription = description }

/**
 * Asks for the date something was done on, such as when a requirement was completed, starting
 * at [initial]. Dates after [today] can't be picked. The picker itself follows the device's
 * language, like other Material labels, and is laid out in that language's direction, so a
 * Persian calendar reads right-to-left (ARCHITECTURE.md, UI layer).
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

/** Dates up to [today]: nothing the scout records can be done in the future. */
private class NotAfter(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toPickerDate() <= today

    override fun isSelectableYear(year: Int) = year <= today.year
}

// The date picker gives dates as the start of their day in UTC, in milliseconds.

private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
