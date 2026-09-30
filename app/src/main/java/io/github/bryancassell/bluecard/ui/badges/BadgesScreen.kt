package io.github.bryancassell.bluecard.ui.badges

import android.icu.text.ListFormatter
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
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LineBreaksAsSpaces
import io.github.bryancassell.bluecard.ui.LoadFailedMessage
import io.github.bryancassell.bluecard.ui.TextLengthLimit
import io.github.bryancassell.bluecard.ui.typedTextFieldStyle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

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
        // and the keyboard stays open as the scout types, and screen readers announce the
        // count when it changes.
        if (uiState is BadgesUiState.Ready || uiState == BadgesUiState.NoMatches) {
            val count = if (uiState is BadgesUiState.Ready) {
                val size = uiState.badges.size
                pluralStringResource(R.plurals.badges_count, size, size)
            } else {
                stringResource(R.string.badges_no_matches)
            }
            // The count when the scout last pressed Clear search, until the count that
            // replaces it is announced.
            var countWhenCleared by remember { mutableStateOf<String?>(null) }
            SearchField(query, onClear = { countWhenCleared = count })
            MatchCount(
                count = count,
                query = query,
                countWhenCleared = countWhenCleared,
                onClearAnnounced = { countWhenCleared = null }
            )
        }
        when (uiState) {
            BadgesUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            BadgesUiState.LoadFailed -> LoadFailedMessage()

            // MatchCount says so.
            BadgesUiState.NoMatches -> Unit

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
private val SearchLengthLimit = TextLengthLimit(maxLength = 100)

@Composable
private fun SearchField(query: TextFieldState, onClear: () -> Unit) {
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
                // field was already focused with the keyboard closed. TalkBack's focus moves
                // to the field too, even when the field already had input focus (checked with
                // TalkBack 17 on Android 17).
                IconButton(onClick = {
                    onClear()
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
        inputTransformation = SearchLengthLimit.then(LineBreaksAsSpaces),
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

/**
 * How long the scout must stop typing before screen readers hear a new count. TalkBack doesn't
 * let new speech cut off a polite live region, so a count spoken while the scout types holds
 * back their keyboard's feedback on the next key.
 */
internal val TypingPause = 1.seconds

/**
 * How many badges the list shows, or that none match. The line shows [count] straight away.
 * Screen readers get it as a polite live region, which they announce when it changes, once the
 * scout has stopped typing in [query] for [TypingPause], so the announcement comes after the
 * keyboard's feedback on the last key.
 *
 * After Clear search, when the count changes from [countWhenCleared], the new count is
 * announced straight away instead, before TalkBack reads the search field the scout is about to
 * type in, and [onClearAnnounced] is called.
 *
 * Compose announces a live region only when a node that's already shown changes (see
 * ARCHITECTURE.md, UI layer), so the live region stays composed as the matches change, and only
 * its text changes.
 */
@Composable
private fun MatchCount(
    count: String,
    query: TextFieldState,
    countWhenCleared: String?,
    onClearAnnounced: () -> Unit
) {
    // Read through derivedStateOf, so the count recomposes when the text changes, not when the
    // cursor moves.
    val typed by remember(query) { derivedStateOf { query.text.toString() } }
    var announced by remember { mutableStateOf(count) }
    // Each keystroke restarts the wait, even one that leaves the count the same.
    LaunchedEffect(count, typed) {
        if (typed.isEmpty() && countWhenCleared != null && count != countWhenCleared) {
            onClearAnnounced()
        } else {
            delay(TypingPause)
        }
        announced = count
    }
    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        // What screen readers hear. TalkBack announces a live region whenever it changes at
        // all, even just its size, so this one is laid out from the announced count alone,
        // apart from the count the line shows. It isn't drawn: the shown count is drawn in its
        // place.
        Text(
            text = announced,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .drawWithContent {}
        )
        Text(
            text = count,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Screen readers get the announced count instead.
            modifier = Modifier.semantics { hideFromAccessibility() }
        )
    }
}
