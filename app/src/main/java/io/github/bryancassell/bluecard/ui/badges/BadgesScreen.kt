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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.maxTextLength
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.ScreenMessage
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle

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

            BadgesUiState.LoadFailed -> LoadFailedMessage()

            BadgesUiState.NoMatches -> ScreenMessage(stringResource(R.string.badges_no_matches))

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

/**
 * Longer than any search needs. The field's text is saved with the screen's state, which has
 * a size limit, so a huge paste mustn't reach it.
 */
private const val MAX_SEARCH_LENGTH = 100

/**
 * Keeps the search to [MAX_SEARCH_LENGTH] characters by cutting off the end of a longer edit,
 * such as a long paste. `InputTransformation.maxLength` rejects the whole edit instead.
 */
private object SearchLengthLimit : InputTransformation {
    override fun SemanticsPropertyReceiver.applySemantics() {
        maxTextLength = MAX_SEARCH_LENGTH
    }

    override fun TextFieldBuffer.transformInput() {
        if (length > MAX_SEARCH_LENGTH) delete(MAX_SEARCH_LENGTH, length)
    }
}

@Composable
private fun SearchField(query: TextFieldState) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    // Read through derivedStateOf, so the field recomposes when the text goes between empty
    // and not, rather than on every keystroke.
    val isEmpty by remember(query) { derivedStateOf { query.text.isEmpty() } }
    OutlinedTextField(
        state = query,
        textStyle = typedTextFieldStyle(),
        label = { Text(stringResource(R.string.badges_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (isEmpty) {
            null
        } else {
            {
                // Clearing usually starts a new search, so the field takes input focus, which
                // the button had until it disappears, and the keyboard opens even if the
                // field was already focused with the keyboard closed. Where TalkBack's focus
                // goes is checked in #71.
                IconButton(onClick = {
                    query.clearText()
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.badges_clear_search)
                    )
                }
            }
        },
        inputTransformation = SearchLengthLimit,
        lineLimits = TextFieldLineLimits.SingleLine,
        // Asks the keyboard not to autocorrect the start of a word into a different word
        // that no longer matches. Some keyboards ignore this: Gboard still corrects typos
        // such as "teh", though it leaves word starts such as "pers" and "cooki" alone.
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search
        ),
        // The list already shows the matches, so the keyboard's search key only closes it.
        onKeyboardAction = { keyboardController?.hide() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .focusRequester(focusRequester)
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
