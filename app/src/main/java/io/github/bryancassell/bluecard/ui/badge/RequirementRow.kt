package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.semantics
import io.github.bryancassell.bluecard.R

// Composables shared by the Badge detail and Requirement detail screens.

/**
 * A requirement's row: its number, summary, "Do N of M" when only some sub-requirements
 * are needed, a check when complete, and a chevron when it opens its own page.
 */
@Composable
fun RequirementRow(
    item: RequirementItem,
    onOpen: (number: String) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        leadingContent = { Text(item.number, style = MaterialTheme.typography.titleMedium) },
        headlineContent = { Text(item.summary) },
        supportingContent = item.choice?.let { { Text(choiceLabel(it)) } },
        trailingContent = if (item.completed || item.opensDetail) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.completed) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.requirement_completed),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.opensDetail) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null
                        )
                    }
                }
            }
        } else {
            null
        },
        modifier = if (item.opensDetail) {
            // Screen readers announce the row as a button that opens the requirement.
            modifier.clickable(
                onClickLabel = stringResource(R.string.requirement_open),
                role = Role.Button,
                onClick = { onOpen(item.number) }
            )
        } else {
            // Screen readers read the row as one item, as they do a row that opens.
            modifier.semantics(mergeDescendants = true) {}
        }
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
