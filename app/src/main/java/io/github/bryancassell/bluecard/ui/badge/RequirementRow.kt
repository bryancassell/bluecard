package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R

// Composables shared by the Badge detail and Requirement detail screens.

/** The smallest a number's box is, on each side. */
private val NumberBoxMinSize = 40.dp

/** Between a number and its box's edge. */
private val NumberBoxPadding = 8.dp

/** A number's text, in its box and when measuring the widest one. */
private val numberStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleMedium

/**
 * A list of requirements' rows. Every number's box is as wide as the widest number needs at the
 * current font size, so the summaries after them line up.
 */
@Composable
fun RequirementRows(
    items: List<RequirementItem>,
    onOpen: (number: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = numberStyle
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Keyed on the numbers alone, so a change in progress doesn't measure them again.
    val numbers = items.map { it.number }
    val numberWidth = remember(numbers, style, textMeasurer, density) {
        val widestNumber = numbers.maxOfOrNull { textMeasurer.measure(it, style).size.width } ?: 0
        with(density) {
            maxOf(NumberBoxMinSize.roundToPx(), widestNumber + NumberBoxPadding.roundToPx() * 2)
                .toDp()
        }
    }
    Column(modifier) {
        items.forEach { RequirementRow(it, numberWidth, onOpen) }
    }
}

/**
 * A requirement's row, which opens its page: its number, in a box that's filled in once it's
 * complete, its summary, "Do N of M" when only some sub-requirements are needed, how much of its
 * tracker is filled in, and "Not needed" when it no longer is. Screen readers read "Completed",
 * "Not completed" or "Not needed" as its state. The scout marks a requirement complete on its
 * page.
 */
@Composable
private fun RequirementRow(
    item: RequirementItem,
    numberWidth: Dp,
    onOpen: (number: String) -> Unit
) {
    val state = stringResource(
        when {
            item.completed -> R.string.requirement_completed
            item.notNeeded -> R.string.requirement_not_needed
            else -> R.string.requirement_not_completed
        }
    )
    ListItem(
        leadingContent = { RequirementNumber(item, numberWidth) },
        headlineContent = { Text(item.summary) },
        supportingContent = if (item.choice == null && item.tracker == null && !item.notNeeded) {
            null
        } else {
            {
                Column {
                    item.choice?.let { Text(choiceLabel(it)) }
                    item.tracker?.let { Text(trackerCountLabel(it)) }
                    if (item.notNeeded) {
                        Text(
                            stringResource(R.string.requirement_not_needed),
                            // Screen readers read it once, first, as the row's state.
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                }
            }
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        },
        // ListItem already reads as one item to screen readers, announced as a button that
        // opens the requirement.
        modifier = Modifier
            .clickable(
                onClickLabel = stringResource(R.string.requirement_open),
                role = Role.Button,
                onClick = { onOpen(item.number) }
            )
            .semantics { stateDescription = state }
    )
}

/**
 * A requirement's number in a box, like the boxes on the blue card: outlined until the
 * requirement is complete, then filled in Scouting America Blue with a check on its top end
 * corner, or filled in grey once it's no longer needed. It's [minWidth] wide, room for the widest
 * number in its list, but still widens to fit its own number if that measures wider. It grows
 * taller with the font size. The check is drawn only: its row reads the state.
 */
@Composable
private fun RequirementNumber(item: RequirementItem, minWidth: Dp) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = minWidth, minHeight = NumberBoxMinSize)
                .then(
                    when {
                        item.completed -> Modifier.background(colors.primary, shape)
                        item.notNeeded -> Modifier.background(colors.surfaceContainerHighest, shape)
                        else -> Modifier.border(2.dp, colors.outline, shape)
                    }
                )
                // On every side: once the text outgrows the box, it stays clear of the edge and
                // below the check.
                .padding(NumberBoxPadding)
        ) {
            Text(
                text = item.number,
                style = numberStyle,
                color = if (item.completed) colors.onPrimary else colors.onSurfaceVariant
            )
        }
        if (item.completed) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // Over the corner. offset, unlike absoluteOffset, mirrors right-to-left.
                    .offset(x = 7.dp, y = (-7).dp)
                    .size(20.dp)
                    // A blue circle under a smaller white one, rather than a border over a
                    // white one, so no white shows at its edge over the blue box.
                    .background(colors.primary, CircleShape)
                    .padding(2.dp)
                    .background(colors.onPrimary, CircleShape)
                    .padding(1.dp)
            )
        }
    }
}

/** "Do 2 of 3". */
@Composable
fun choiceLabel(choice: Choice): String =
    stringResource(R.string.requirement_choice, choice.required, choice.of)

/** Shown while the catalog loads. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
