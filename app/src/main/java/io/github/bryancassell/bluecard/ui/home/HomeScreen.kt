package io.github.bryancassell.bluecard.ui.home

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R

/** Connects the Home screen to its ViewModel. */
@Composable
fun HomeRoute(
    onOpenBadges: () -> Unit,
    onOpenDataManagement: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onOpenBadges = onOpenBadges,
        onOpenDataManagement = onOpenDataManagement,
        modifier = modifier
    )
}

/** The scout's name and unit, and a summary of their merit badge progress. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenBadges: () -> Unit,
    onOpenDataManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        HomeUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        is HomeUiState.Ready -> Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = uiState.name,
                    style = MaterialTheme.typography.headlineMedium,
                    // Lets screen reader users jump to it.
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.home_unit, uiState.unitNumber),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (uiState.hasNoProgress) {
                Text(stringResource(R.string.home_no_progress))
            } else {
                Summary(uiState)
            }
            Button(onClick = onOpenBadges, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_badges))
            }
            OutlinedButton(onClick = onOpenDataManagement, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_data_management))
            }
        }
    }
}

@Composable
private fun Summary(uiState: HomeUiState.Ready) {
    SummaryCard(title = stringResource(R.string.home_badges_title)) {
        Text(countText(R.plurals.home_completed, uiState.badges.completed))
        Text(countText(R.plurals.home_in_progress, uiState.badges.inProgress))
    }
    // A catalog with no Eagle-required badges has no Eagle progress to show.
    if (uiState.eagleTotal > 0) {
        SummaryCard(title = stringResource(R.string.home_eagle_title)) {
            Text(
                pluralStringResource(
                    R.plurals.home_eagle_completed,
                    uiState.eagle.completed,
                    uiState.eagle.completed,
                    uiState.eagleTotal
                )
            )
            LinearProgressIndicator(
                progress = { uiState.eagle.completed.toFloat() / uiState.eagleTotal },
                modifier = Modifier.fillMaxWidth()
            )
            Text(countText(R.plurals.home_in_progress, uiState.eagle.inProgress))
        }
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

/** A plural string whose only argument is its quantity, such as "3 in progress". */
@Composable
private fun countText(@PluralsRes id: Int, count: Int) = pluralStringResource(id, count, count)
