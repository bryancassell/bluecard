package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadFailedMessage

/** Connects the Requirement detail screen to its ViewModel. */
@Composable
fun RequirementDetailRoute(
    badgeId: String,
    number: String,
    onOpenRequirement: (number: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequirementDetailViewModel =
        hiltViewModel<RequirementDetailViewModel, RequirementDetailViewModel.Factory> {
            it.create(badgeId, number)
        }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RequirementDetailScreen(
        uiState = uiState,
        onOpenRequirement = onOpenRequirement,
        modifier = modifier
    )
}

/**
 * A requirement with more than fits in a row on the badge's page: its sub-requirements
 * now, and its tracker once trackers are built. A sub-requirement with more to it opens
 * its own page in turn.
 */
@Composable
fun RequirementDetailScreen(
    uiState: RequirementDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        RequirementDetailUiState.Loading -> LoadingIndicator(modifier)

        RequirementDetailUiState.LoadFailed -> LoadFailedMessage(modifier)

        RequirementDetailUiState.Unavailable ->
            UnavailableMessage(stringResource(R.string.requirement_detail_unavailable), modifier)

        is RequirementDetailUiState.Ready -> Column(
            modifier = modifier.verticalScroll(rememberScrollState())
        ) {
            val requirement = uiState.requirement
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.badgeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.requirement_detail_title, requirement.number),
                    style = MaterialTheme.typography.headlineMedium,
                    // Lets screen reader users jump to it.
                    modifier = Modifier.semantics { heading() }
                )
                Text(text = requirement.summary, style = MaterialTheme.typography.bodyLarge)
                requirement.choice?.let {
                    Text(
                        text = choiceLabel(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (requirement.completed) {
                    Text(
                        text = stringResource(R.string.requirement_completed),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            uiState.children.forEach { RequirementRow(item = it, onOpen = onOpenRequirement) }
        }
    }
}
