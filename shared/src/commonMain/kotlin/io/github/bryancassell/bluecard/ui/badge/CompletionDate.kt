package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.labeled_date_button
import io.github.bryancassell.bluecard.resources.requirement_add_date
import io.github.bryancassell.bluecard.resources.requirement_change_date
import io.github.bryancassell.bluecard.resources.requirement_remove_date
import io.github.bryancassell.bluecard.ui.ButtonText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
    removeText: StringResource = Res.string.requirement_remove_date,
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
                Res.string.requirement_add_date
            } else {
                Res.string.requirement_change_date
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
            description = label?.let { stringResource(Res.string.labeled_date_button, text, it) }
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
        CompletionDatePicker(
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
 * instead of a later [initial], such as a date recorded while the device's clock was ahead.
 * [onConfirm] gets the date picked, and [onDismiss] is called if the scout cancels.
 */
@Composable
internal expect fun CompletionDatePicker(
    initial: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
)
