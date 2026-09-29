package io.github.bryancassell.bluecard.ui.badge

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.SaveFailedSnackbarHost
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.badges.eagleRequirementLabel
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter

/** Connects the Badge detail screen to its ViewModel. */
@Composable
fun BadgeDetailRoute(
    badgeId: String,
    onOpenRequirement: (number: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BadgeDetailViewModel =
        hiltViewModel<BadgeDetailViewModel, BadgeDetailViewModel.Factory> { it.create(badgeId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BadgeDetailScreen(
        uiState = uiState,
        onOpenRequirement = onOpenRequirement,
        onCompletedChange = viewModel::setCompleted,
        onSaveFailureShown = viewModel::onSaveFailureShown,
        modifier = modifier
    )
}

/**
 * A badge's summary, whether it's Eagle-required, a link to its official page, and its
 * top-level requirements, which the scout can mark complete. Each requirement opens its own
 * page for the rest, which keeps this one short.
 */
@Composable
fun BadgeDetailScreen(
    uiState: BadgeDetailUiState,
    onOpenRequirement: (number: String) -> Unit,
    onCompletedChange: (number: String, completed: Boolean) -> Unit,
    onSaveFailureShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        BadgeDetailUiState.Loading -> LoadingIndicator(modifier)

        BadgeDetailUiState.LoadFailed -> LoadFailedMessage(modifier)

        BadgeDetailUiState.Unavailable ->
            ScreenMessage(stringResource(R.string.requirements_unavailable), modifier)

        is BadgeDetailUiState.Ready -> Box(modifier = modifier) {
            BadgeDetails(uiState, onOpenRequirement, onCompletedChange)
            SaveFailedSnackbarHost(
                saveFailed = uiState.saveFailed,
                onShown = onSaveFailureShown,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun BadgeDetails(
    uiState: BadgeDetailUiState.Ready,
    onOpenRequirement: (number: String) -> Unit,
    onCompletedChange: (number: String, completed: Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = uiState.name,
                style = MaterialTheme.typography.headlineMedium,
                // Lets screen reader users jump to it.
                modifier = Modifier.semantics { heading() }
            )
            uiState.eagle?.let {
                Text(
                    text = eagleRequirementLabel(it, rememberBadgeNameListFormatter()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = uiState.summary, style = MaterialTheme.typography.bodyLarge)
        }
        // Opens the official page in the browser.
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        val noBrowser = stringResource(R.string.badge_detail_no_browser)
        TextButton(
            onClick = {
                try {
                    uriHandler.openUri(uiState.officialUrl)
                } catch (_: IllegalArgumentException) {
                    // What Compose's UriHandler throws when no app can open web links,
                    // as when parental controls block the browser.
                    Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(text = stringResource(R.string.badge_detail_official_page))
        }
        Text(
            text = stringResource(R.string.badge_detail_requirements),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        uiState.requirements.forEach {
            RequirementRow(
                item = it,
                onOpen = onOpenRequirement,
                onCompletedChange = onCompletedChange
            )
        }
    }
}
