package io.github.bryancassell.bluecard.ui.badge

import android.text.TextUtils
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
 * starting": moving from control to control, they don't hear a label shown above the date, as on
 * Data management (#166). It comes after the button's text, as WCAG 2.5.3 recommends for voice
 * control users (#256). The text is in [textStyle].
 *
 * Most date columns are named just "Date", so their buttons read "Add date: Date". No tracker has
 * two date columns to tell apart, but leaving the label out only for "Date" would compare against
 * catalog text, in every translation too, and naming the columns more specifically, such as "Date
 * of hike", would change what sighted scouts see across many badges (#263).
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
        // Lines the buttons' text up with the date's. If they don't fit side by side, as at the
        // largest text and display size, the second goes below the first rather than squeezing
        // its label until a word breaks, as "Unmar" / "k" did (#307), as far below as the date
        // picker's OK goes. They're read and focused in the same order either way.
        FlowRow(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
 *
 * On a window narrower than the calendar, such as a phone's at the largest display size, the
 * scout types the date instead, as Material suggests where space is short, with the keyboard down
 * until they tap the field, so they first see the whole dialog, Cancel included. Smaller touch
 * targets would have kept the calendar, but the largest display size is chosen by those who most
 * need big ones (#306).
 */
// BasicAlertDialog is what Material's DatePickerDialog is built on, so the dialog is otherwise
// the same, and screen readers hear it the same. Material's is always as wide as the calendar,
// so on a narrower window its last days and OK were off the screen (#306). BasicAlertDialog is
// experimental, so a change to it would fail the build when Compose is updated. A change to
// DatePickerDialog wouldn't reach this one, so compare the two when updating Material 3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides pickerLayoutDirection()) {
        BasicAlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.wrapContentHeight(),
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                // In pixels, as the calendar is laid out, so it shows whenever it fits.
                val calendarFits =
                    constraints.maxWidth >= with(LocalDensity.current) { CalendarWidth.roundToPx() }
                val state = rememberDatePickerState(
                    initialSelectedDateMillis = initial.coerceAtMost(today).toPickerMillis(),
                    selectableDates = remember(today) { NotAfter(today) },
                    initialDisplayMode = if (calendarFits) DisplayMode.Picker else DisplayMode.Input
                )
                if (!calendarFits && state.displayMode == DisplayMode.Picker) {
                    // The calendar comes back after the window changes size, as when the phone
                    // turns or is folded with it showing, and may no longer fit. Nothing shows
                    // until the picker switches to typing, so it isn't drawn squeezed first.
                    SideEffect { state.displayMode = DisplayMode.Input }
                } else {
                    DatePickerSurface(state, calendarFits, maxWidth, onConfirm, onDismiss)
                }
            }
        }
    }
}

/**
 * The dialog's surface, as Material's DatePickerDialog draws it, with the picker for [state] and
 * its buttons. It's as wide as the calendar where it [calendarFits], and otherwise
 * [windowWidth] less a margin each side, where the scout types the date.
 */
@Composable
private fun DatePickerSurface(
    state: DatePickerState,
    calendarFits: Boolean,
    windowWidth: Dp,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    // Material focuses the field, opening the keyboard, when it's given this. It's given once
    // the calendar has shown, so the field takes focus when the scout switches to typing, not
    // when the picker opens to typing or comes back typing after the window changes size. It's
    // set while composing, before it's read: set a frame later, from a SideEffect, it didn't
    // reach the field when the scout switched from the calendar back to typing.
    var calendarShown by remember { mutableStateOf(false) }
    if (state.displayMode == DisplayMode.Picker) calendarShown = true
    val focusRequester = remember { FocusRequester() }
    Surface(
        // Sized rather than padded, so a tap in the margin is outside the dialog and closes it.
        // On a window under 312dp, Material's dialogs' 280dp least width takes part of it.
        modifier = Modifier
            .width(if (calendarFits) CalendarWidth else windowWidth - NarrowMargin * 2)
            .heightIn(max = MaxHeight),
        shape = DatePickerDefaults.shape,
        color = DatePickerDefaults.colors().containerColor,
        tonalElevation = DatePickerDefaults.TonalElevation
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            // On a short window, such as a phone's in landscape, the dialog is shorter than the
            // picker. Material would then clip the month's first and last weeks, leaving days
            // under 48dp tall or hidden (#282), so the picker scrolls instead, as Material's own
            // sample does, and the buttons stay below it. The sample also suggests opening to
            // typing on a short window, but opening the calendar wherever it fits keeps the
            // picker the same when the phone turns, and the keyboard down until the scout
            // chooses to type. It opens scrolled to the top, so the selected day can be a swipe
            // away: opening at its week would depend on Material's private layout sizes, which
            // an update could change.
            Box(Modifier.weight(1f, fill = false)) {
                DatePicker(
                    state = state,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    showModeToggle = calendarFits,
                    focusRequester = focusRequester.takeIf { calendarFits && calendarShown }
                )
            }
            DatePickerButtons(state, onConfirm, onDismiss)
        }
    }
}

/**
 * The width Material's calendar is laid out for: seven days with 48dp touch targets, and its
 * padding. Material's own constant for it is internal.
 */
private val CalendarWidth = 360.dp

/** Material's DatePickerDialog's greatest height, whose constant is also internal. */
private val MaxHeight = 568.dp

/** The margin each side of the dialog where the calendar doesn't fit: the pages' side margin. */
private val NarrowMargin = 16.dp

/**
 * Cancel and OK, at the end of a row as in Material's DatePickerDialog. If they don't fit side by
 * side, OK goes below Cancel, so they're read and focused in the same order either way, unlike
 * Material's dialogs, the app's others included, which put OK above Cancel. They fit side by side
 * on every phone at the largest text size, so only a narrower window stacks them. OK gives
 * [onConfirm] the date [state] has selected.
 */
@Composable
private fun ColumnScope.DatePickerButtons(
    state: DatePickerState,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .align(Alignment.End)
            .padding(bottom = 8.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.date_picker_cancel))
        }
        // Null while a typed date isn't valid. One the picker doesn't offer can still be
        // selected when it's restored with an earlier today, as when the system stopped the app
        // and the scout then crossed a date line westward.
        val selected = state.selectedDateMillis?.takeIf(state.selectableDates::isSelectableDate)
        TextButton(
            onClick = { selected?.let { onConfirm(it.toPickerDate()) } },
            enabled = selected != null
        ) {
            Text(stringResource(R.string.date_picker_ok))
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
