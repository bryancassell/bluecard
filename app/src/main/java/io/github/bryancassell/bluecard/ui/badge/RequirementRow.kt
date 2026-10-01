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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.bryancassell.bluecard.R

// Composables shared by the Badge detail and Requirement detail screens.

/**
 * A requirement's row, which opens its page: its number, in a box that's filled in once it's
 * complete, its summary, "Do N of M" when only some sub-requirements are needed, how much of its
 * tracker is filled in, and "Not needed" when it no longer is. Screen readers read "Completed",
 * "Not completed" or "Not needed" as its state. The scout marks a requirement complete on its
 * page.
 */
@Composable
fun RequirementRow(
    item: RequirementItem,
    onOpen: (number: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = stringResource(
        when {
            item.completed -> R.string.requirement_completed
            item.notNeeded -> R.string.requirement_not_needed
            else -> R.string.requirement_not_completed
        }
    )
    ListItem(
        leadingContent = { RequirementNumber(item) },
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
        modifier = modifier
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
 * corner, or filled in grey once it's no longer needed. It grows with a long number, such as
 * "8a(1)", and with the font size. The check is drawn only: its row reads the state.
 */
@Composable
private fun RequirementNumber(item: RequirementItem) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                .then(
                    when {
                        item.completed -> Modifier.background(colors.primary, shape)
                        item.notNeeded -> Modifier.background(colors.surfaceContainerHighest, shape)
                        else -> Modifier.border(2.dp, colors.outline, shape)
                    }
                )
                // On every side: once the text outgrows the box, it stays clear of the edge and
                // below the check.
                .padding(8.dp)
        ) {
            Text(
                text = item.number,
                style = MaterialTheme.typography.titleMedium,
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
