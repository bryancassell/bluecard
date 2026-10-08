package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.LoadingOrMessage
import io.github.bryancassell.bluecard.ui.singleLineInput
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
    HeadingSearchAndContent(
        heading = {
            Text(
                text = stringResource(R.string.badges_title),
                style = MaterialTheme.typography.headlineMedium,
                // Lets screen reader users jump to it.
                modifier = Modifier
                    .padding(16.dp)
                    .semantics { heading() }
            )
        },
        search = { SearchAndCount(uiState, query) },
        content = { BadgesContent(uiState, onOpenBadge) },
        // Ends the list above the keyboard, so every match can be scrolled into view.
        modifier = modifier.imePadding()
    )
}

/**
 * Lays out [heading], [search] and [content] from the top down, as a Column would, except that
 * [heading] is left out when the page is too short for it and [search] together. [content] gets
 * the room left under them.
 *
 * A phone in landscape leaves the page 107dp above the keyboard. The heading kept 68dp of it,
 * squeezing the search field into a strip that hid what the scout typed, with no room for the
 * count (#308). With the heading left out, the field and count fit, and the heading comes back
 * when the keyboard closes. It isn't placed, so screen readers don't read it either: Compose
 * leaves unplaced nodes out of what it gives them.
 *
 * The rule takes [search]'s height with no line wrapped, which stays the same as the scout types.
 * Its laid-out height doesn't: the field's label can wrap until the scout types, and the count's
 * text can wrap, then change height again a second later as the count screen readers hear catches
 * up (see MatchCount). The heading came and went with it, moving the field as the scout typed. On
 * a page with just enough room, a line that wraps is cut off instead.
 */
@Composable
private fun HeadingSearchAndContent(
    heading: @Composable () -> Unit,
    search: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        contents = listOf(heading, search, content),
        modifier = modifier
    ) { (headingMeasurables, searchMeasurables, contentMeasurables), constraints ->
        val headingHeight = headingMeasurables.sumOf { it.minIntrinsicHeight(constraints.maxWidth) }
        val searchHeight = searchMeasurables.sumOf { it.minIntrinsicHeight(Constraints.Infinity) }
        val showHeading = headingHeight + searchHeight <= constraints.maxHeight
        val children = (if (showHeading) headingMeasurables else emptyList()) +
            searchMeasurables + contentMeasurables
        // Each child gets the room the ones above it leave, as in a Column.
        var spaceLeft = constraints.maxHeight
        val placeables = children.map { child ->
            child.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = spaceLeft))
                .also { spaceLeft -= it.height }
        }
        // As big as what it places, as a Column is.
        layout(
            width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtLeast(constraints.minWidth),
            height = placeables.sumOf { it.height }.coerceAtLeast(constraints.minHeight)
        ) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
        }
    }
}

/** The search field and how many badges match, once the badges have loaded. */
@Composable
private fun SearchAndCount(uiState: BadgesUiState, query: TextFieldState) {
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
}

/** The badges, or the loading indicator or load failure in their place. */
@Composable
private fun BadgesContent(uiState: BadgesUiState, onOpenBadge: (badgeId: String) -> Unit) {
    when (uiState) {
        // One branch, so screen readers hear the message (see LoadingOrMessage).
        BadgesUiState.Loading, BadgesUiState.LoadFailed -> LoadingOrMessage(
            message = when (uiState) {
                BadgesUiState.LoadFailed -> stringResource(R.string.load_failed)
                else -> null
            }
        )

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
            val badgeCount = uiState.badges.size
            LazyColumn(
                state = listState,
                // Screen readers say how many badges the list has. LazyColumn's count
                // can stay the old list state's after each new set of matches above:
                // LazyLayoutSemanticsModifierNode.update() takes a new state without
                // invalidating semantics (Compose 1.12.1). Compose applies a modifier's
                // semantics after LazyColumn's own, so this count is the one screen
                // readers get.
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        collectionInfo = CollectionInfo(rowCount = badgeCount, columnCount = 1)
                    }
            ) {
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

/**
 * Longer than any search needs. The field's text is saved with the screen's state, which has
 * a size limit, so a huge paste mustn't reach it.
 */
private val SearchInput = singleLineInput(maxLength = 100)

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
        inputTransformation = SearchInput,
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
 * The count isn't read out when the screen first shows it, as Badges opens or comes back from a
 * badge: it becomes a live region only when it first changes, and stays one. Compose reports
 * each change to a node's size or position, its first layout included, as a change to that node
 * (`onLayoutChange` in `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1), and
 * TalkBack reads a live region on any change it's the source of. A count that was a live region
 * from the start was read out as the screen opened, and held back its heading by 2 seconds
 * (#278). So the count stays composed as the matches change, and only its text changes: a new
 * node wouldn't be a live region.
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
    var isLiveRegion by remember { mutableStateOf(false) }
    // Each keystroke restarts the wait, even one that leaves the count the same.
    LaunchedEffect(count, typed) {
        if (typed.isEmpty() && countWhenCleared != null && count != countWhenCleared) {
            onClearAnnounced()
        } else {
            delay(TypingPause)
        }
        // Set with the new count, so the change that makes it a live region is read out.
        if (count != announced) isLiveRegion = true
        announced = count
    }
    // Read here rather than in the semantics block, so the count becomes a live region as its new
    // text is composed. Compose updates a semantics block as soon as a state it reads changes,
    // and TalkBack read out the old count then.
    val isLiveRegionComposed = isLiveRegion
    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        // What screen readers hear. TalkBack announces a live region whenever it changes at
        // all, even just its size, so this one is laid out from the announced count alone,
        // apart from the count the line shows. It isn't drawn: the shown count is drawn in its
        // place.
        Text(
            text = announced,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .semantics { if (isLiveRegionComposed) liveRegion = LiveRegionMode.Polite }
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
