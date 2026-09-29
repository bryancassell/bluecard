package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.BadgeStatus

/** Connects the Badges screen to its ViewModel. */
@Composable
fun BadgesRoute(
    onOpenBadge: (badgeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BadgesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BadgesScreen(
        uiState = uiState,
        query = viewModel.query,
        onOpenBadge = onOpenBadge,
        modifier = modifier
    )
}

/**
 * Every merit badge, or the ones that match the scout's search in [query], with whether
 * it's Eagle-required and the scout's progress on it.
 */
@Composable
fun BadgesScreen(
    uiState: BadgesUiState,
    query: TextFieldState,
    onOpenBadge: (badgeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ends the list above the keyboard, so every match can be scrolled into view.
    Column(modifier = modifier.imePadding()) {
        Text(
            text = stringResource(R.string.badges_title),
            style = MaterialTheme.typography.headlineMedium,
            // Lets screen reader users jump to it.
            modifier = Modifier
                .padding(16.dp)
                .semantics { heading() }
        )
        // Shown in the same place whether or not anything matches, so the field keeps focus
        // and the keyboard stays open as the scout types.
        if (uiState is BadgesUiState.Ready || uiState == BadgesUiState.NoMatches) {
            SearchField(query)
        }
        when (uiState) {
            BadgesUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            BadgesUiState.NoMatches -> Text(
                text = stringResource(R.string.badges_no_matches),
                modifier = Modifier
                    .padding(16.dp)
                    // Read out by screen readers when it appears.
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )

            // A lazy list composes only the rows on screen, so the full catalog scrolls
            // smoothly. Keys keep each row's state with its badge.
            is BadgesUiState.Ready -> {
                val listFormatter = rememberBadgeNameListFormatter()
                // New matches are shown from the top. Otherwise the list would keep the first
                // badge on screen in place and hide the matches above it. The same badges,
                // such as when the scout comes back from one, keep their scroll position.
                val listState = rememberSaveable(
                    uiState.badges.map { it.id },
                    saver = LazyListState.Saver
                ) { LazyListState() }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(uiState.badges, key = { it.id }) { badge ->
                        BadgeRow(
                            badge = badge,
                            listFormatter = listFormatter,
                            onClick = { onOpenBadge(badge.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: TextFieldState) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        state = query,
        label = { Text(stringResource(R.string.badges_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.text.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { query.clearText() }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.badges_clear_search)
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The list already shows the matches, so the keyboard's search key only closes it.
        onKeyboardAction = { keyboardController?.hide() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun BadgeRow(badge: BadgeListItem, listFormatter: ListFormatter, onClick: () -> Unit) {
    val eagle = badge.eagle?.let { eagleRequirementLabel(it, listFormatter) }
    val status = when (badge.status) {
        BadgeStatus.NotStarted -> null
        BadgeStatus.InProgress -> stringResource(R.string.badges_in_progress)
        BadgeStatus.Completed -> stringResource(R.string.badges_completed)
    }
    ListItem(
        headlineContent = { Text(badge.name) },
        supportingContent = eagle?.let { { Text(it) } },
        trailingContent = status?.let { { Text(it) } },
        // Screen readers announce the row as a button that opens the badge.
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.badges_open_badge),
            role = Role.Button,
            onClick = onClick
        )
    )
}
