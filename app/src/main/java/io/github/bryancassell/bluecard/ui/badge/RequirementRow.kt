package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.bryancassell.bluecard.R

// Composables shared by the Badge detail and Requirement detail screens.

/**
 * A requirement's row, which opens its page: its number, summary, "Do N of M" when only some
 * sub-requirements are needed, and whether it's complete. A requirement the scout marks
 * complete has a checkbox for it; one with sub-requirements has a check once enough are done.
 */
@Composable
fun RequirementRow(
    item: RequirementItem,
    onOpen: (number: String) -> Unit,
    onCompletedChange: (number: String, completed: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = { Text(item.number, style = MaterialTheme.typography.titleMedium) },
        headlineContent = { Text(item.summary) },
        supportingContent = item.choice?.let { { Text(choiceLabel(it)) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.hasSubRequirements) {
                    // Its own item for screen readers, named by the requirement's number,
                    // since it may be reached without the row.
                    val label = stringResource(R.string.requirement_completed_checkbox, item.number)
                    Checkbox(
                        checked = item.completed,
                        onCheckedChange = { onCompletedChange(item.number, it) },
                        modifier = Modifier.semantics { contentDescription = label }
                    )
                } else if (item.completed) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.requirement_completed),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
            }
        },
        // ListItem already reads as one item to screen readers, announced as a button that
        // opens the requirement.
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.requirement_open),
            role = Role.Button,
            onClick = { onOpen(item.number) }
        )
    )
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
