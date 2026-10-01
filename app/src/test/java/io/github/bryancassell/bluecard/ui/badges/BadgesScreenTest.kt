package io.github.bryancassell.bluecard.ui.badges

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.testing.paragraphDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class BadgesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedBadges = mutableListOf<String>()

    private val badges = listOf(
        BadgeListItem("camping", "Camping", EagleRequirement.Required, BadgeStatus.Completed),
        BadgeListItem("chess", "Chess", eagle = null, BadgeStatus.InProgress, fractionDone = 0.4f),
        BadgeListItem("cooking", "Cooking", EagleRequirement.Required, BadgeStatus.NotStarted),
        BadgeListItem(
            "hiking",
            "Hiking",
            EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming")),
            BadgeStatus.NotStarted
        )
    )

    private val query = TextFieldState()

    /** Records what the screen asks of the on-screen keyboard. */
    private val keyboard = object : SoftwareKeyboardController {
        var shows = 0
        var hides = 0

        override fun show() {
            shows++
        }

        override fun hide() {
            hides++
        }
    }

    // Tests can change it after show(), as the ViewModel would.
    private var uiState by mutableStateOf<BadgesUiState>(BadgesUiState.Loading)

    // The view that hosts the screen, which connects the keyboard to the focused field.
    private lateinit var view: View

    private fun show(state: BadgesUiState) {
        uiState = state
        composeTestRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                BadgesScreen(uiState = uiState, query = query, onOpenBadge = { openedBadges += it })
            }
        }
    }

    // Each row merges its texts, so a row is the node with the badge's name.
    private fun row(name: String) = composeTestRule.onNodeWithText(name)

    private fun list() = composeTestRule.onNode(hasScrollToNodeAction())

    private fun searchField() = composeTestRule.onNode(hasSetTextAction())

    private fun clearButton() = composeTestRule.onNodeWithContentDescription("Clear search")

    // The count as the screen shows it. Screen readers get announcedCount() instead.
    private fun shownCount() = composeTestRule.onNode(
        hasText("merit badge", substring = true) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
    )

    // The count as screen readers hear it: the line's live region.
    private fun announcedCount() = composeTestRule.onNode(
        hasText("merit badge", substring = true) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
    )

    private fun noMatchesMessage() = composeTestRule.onNode(
        hasText("No merit badges match your search.") and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
    )

    /**
     * Waits as long as the scout must stop typing before screen readers hear a new count, from
     * when the screen has caught up with the last change.
     */
    private fun pauseTyping() = waitFor(TypingPause.inWholeMilliseconds)

    // Most of the pause, then enough more to pass it.
    private val mostOfPause = TypingPause.inWholeMilliseconds - 200
    private val restOfPause = 300L

    /**
     * Lets the screen catch up with the last change, waits [milliseconds] more, then lets it
     * show what changed while it waited.
     */
    private fun waitFor(milliseconds: Long) {
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(milliseconds)
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun catchUp() = waitFor(0)

    private val many = (1..200).map {
        BadgeListItem("badge-$it", "Badge $it", eagle = null, BadgeStatus.NotStarted)
    }

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    @Test
    fun loading_showsProgressAndNoBadges() {
        show(BadgesUiState.Loading)

        composeTestRule.onNodeWithText("Merit badges").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        list().assertDoesNotExist()
        searchField().assertDoesNotExist()
        shownCount().assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsTitleAndMessage() {
        show(BadgesUiState.LoadFailed)

        composeTestRule.onNodeWithText("Merit badges").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        list().assertDoesNotExist()
        searchField().assertDoesNotExist()
        shownCount().assertDoesNotExist()
    }

    @Test
    fun ready_showsBadgesInGivenOrder() {
        show(BadgesUiState.Ready(badges))

        composeTestRule.onNodeWithText("Merit badges").assert(isHeading())
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        val rows = list().onChildren()
        rows[0].assert(hasText("Camping"))
        rows[1].assert(hasText("Chess"))
        rows[2].assert(hasText("Cooking"))
        rows[3].assert(hasText("Hiking"))
    }

    @Test
    fun rows_areButtonsThatOpenTheBadge() {
        show(BadgesUiState.Ready(badges))

        row("Chess")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label is \"open badge\"") {
                    it.config[SemanticsActions.OnClick].label == "open badge"
                }
            )
    }

    @Test
    fun eagleRequiredBadges_areLabeled() {
        show(BadgesUiState.Ready(badges))

        row("Camping").assert(hasText("Eagle-required"))
        row("Cooking").assert(hasText("Eagle-required"))
        row("Chess").assert(!hasText("Eagle-required"))
    }

    @Test
    fun eagleGroupBadge_namesTheGroup() {
        show(BadgesUiState.Ready(badges))

        row("Hiking").assert(hasText("Eagle-required (one of Cycling, Hiking, and Swimming)"))
        row("Hiking").assert(!hasText("Eagle-required"))
    }

    // The app has only English strings, so on a French device the whole label stays
    // English, rather than an English sentence with a French list ("Hiking et Swimming").
    @Config(qualifiers = "fr")
    @Test
    fun eagleGroupBadge_onDeviceInAnotherLanguage_staysInOneLanguage() {
        show(BadgesUiState.Ready(badges))

        row("Hiking").assert(hasText("Eagle-required (one of Cycling, Hiking, and Swimming)"))
    }

    @Test
    fun completedBadge_isLabeledCompleted() {
        show(BadgesUiState.Ready(badges))

        row("Camping").assert(hasText("Completed"))
        row("Camping").assert(!hasText("In progress"))
    }

    @Test
    fun inProgressBadge_isLabeledInProgress() {
        show(BadgesUiState.Ready(badges))

        row("Chess").assert(hasText("In progress"))
        row("Chess").assert(!hasText("Completed"))
    }

    @Test
    fun notStartedBadge_hasNoStatusLabel() {
        show(BadgesUiState.Ready(badges))

        row("Cooking").assert(!hasText("In progress"))
        row("Cooking").assert(!hasText("Completed"))
    }

    private val anyProgressBar =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun inProgressBadge_showsHowMuchIsDone_asABar() {
        show(BadgesUiState.Ready(badges))

        // Only Chess is in progress. The bar is in the row, under its name.
        composeTestRule.onAllNodes(anyProgressBar, useUnmergedTree = true).assertCountEquals(1)
        val fortyPercent = hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))
        val bar = composeTestRule.onNode(fortyPercent, useUnmergedTree = true)
            .assertIsDisplayed()
            .getBoundsInRoot()
        val name = composeTestRule.onNodeWithText("Chess", useUnmergedTree = true).getBoundsInRoot()
        val row = row("Chess").getBoundsInRoot()
        // Material makes the bar's bounds taller than the bar, for touch, so its middle says
        // where it is.
        assertTrue((bar.top + bar.bottom) / 2 in name.bottom..row.bottom)
    }

    @Test
    fun inProgressBadge_readsHowMuchIsDone_asTheRowsState() {
        show(BadgesUiState.Ready(badges))

        row("Chess").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "40% done")
        )
        // Screen readers hear it from the row, so they skip the bar.
        composeTestRule.onNode(anyProgressBar, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test
    fun badgesNotInProgress_haveNoState() {
        show(BadgesUiState.Ready(badges))

        for (name in listOf("Camping", "Cooking", "Hiking")) {
            row(name).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }

    @Test
    fun inProgressBadge_withEagleLabel_showsBarUnderIt() {
        val camping = badges.first().copy(status = BadgeStatus.InProgress, fractionDone = 0.25f)
        show(BadgesUiState.Ready(listOf(camping)))

        val bar = composeTestRule.onNode(anyProgressBar, useUnmergedTree = true).getBoundsInRoot()
        val eagle = composeTestRule.onNodeWithText("Eagle-required", useUnmergedTree = true)
            .getBoundsInRoot()
        assertTrue((bar.top + bar.bottom) / 2 > eagle.bottom)
    }

    @Test
    fun clickingBadge_opensIt() {
        show(BadgesUiState.Ready(badges))

        row("Chess").performClick()

        assertEquals(listOf("chess"), openedBadges)
    }

    @Test
    fun longList_scrollsToLastBadge() {
        // More badges than the full catalog (about 140).
        show(BadgesUiState.Ready(many))
        // Rows off screen aren't composed until scrolled to.
        row("Badge 200").assertDoesNotExist()

        list().performScrollToNode(hasText("Badge 200"))

        row("Badge 200").assertIsDisplayed()
        row("Badge 200").performClick()
        assertEquals(listOf("badge-200"), openedBadges)
    }

    @Test
    fun ready_showsEmptySearchFieldWithoutClearButton() {
        show(BadgesUiState.Ready(badges))

        searchField().assertIsDisplayed().assert(hasText("Search merit badges"))
        clearButton().assertDoesNotExist()
    }

    @Test
    fun typingInSearchField_updatesQuery() {
        show(BadgesUiState.Ready(badges))

        searchField().performTextInput("camp")

        assertEquals("camp", query.text.toString())
    }

    // The layout is left-to-right, like the English strings, but a search typed in Persian
    // reads right-to-left.
    @Test
    fun searchTypedInPersian_readsRightToLeft() {
        show(BadgesUiState.Ready(badges))

        searchField().performTextInput("شنا.")

        assertEquals(ResolvedTextDirection.Rtl, searchField().paragraphDirection())
    }

    @Test
    fun clearButton_emptiesSearchField() {
        query.setTextAndPlaceCursorAtEnd("camp")
        show(BadgesUiState.Ready(badges))

        clearButton().assertIsDisplayed().performClick()

        assertEquals("", query.text.toString())
        clearButton().assertDoesNotExist()
    }

    @Test
    fun clearButton_movesFocusToSearchField() {
        query.setTextAndPlaceCursorAtEnd("camp")
        show(BadgesUiState.Ready(badges))

        clearButton().performClick()

        searchField().assertIsFocused()
    }

    @Test
    fun clearButton_opensKeyboard_evenWhenFieldIsAlreadyFocused() {
        show(BadgesUiState.Ready(badges))
        searchField().performTextInput("camp")
        // The keyboard's search key closes the keyboard, and the field keeps focus.
        searchField().performImeAction()

        clearButton().performClick()

        searchField().assertIsFocused()
        assertEquals(1, keyboard.shows)
    }

    @Test
    fun searchField_trimsTextPast100Characters() {
        show(BadgesUiState.Ready(badges))
        searchField().performTextInput("a".repeat(90))

        // As when pasting.
        searchField().performTextInput("b".repeat(20))

        assertEquals("a".repeat(90) + "b".repeat(10), query.text.toString())
        searchField().assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 100))
    }

    // A single-line field shows a pasted line break as a space, so the query holds one.
    @Test
    fun searchField_pastedLineBreak_becomesSpace() {
        show(BadgesUiState.Ready(badges))

        searchField().performTextInput("First\nAid")

        assertEquals("First Aid", query.text.toString())
    }

    @Test
    fun searchField_asksKeyboardForSearchKeyWithoutAutocorrect() {
        show(BadgesUiState.Ready(badges))
        searchField().performClick()

        val editorInfo = EditorInfo()
        composeTestRule.runOnIdle { view.onCreateInputConnection(editorInfo) }

        assertEquals(
            EditorInfo.IME_ACTION_SEARCH,
            editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        )
        assertEquals(0, editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
    }

    @Test
    fun keyboardSearch_hidesKeyboard() {
        show(BadgesUiState.Ready(badges))

        searchField().performImeAction()

        assertEquals(1, keyboard.hides)
    }

    @Test
    fun ready_showsHowManyBadges() {
        show(BadgesUiState.Ready(badges))

        shownCount().assertIsDisplayed().assert(hasText("4 merit badges"))
        announcedCount().assert(hasText("4 merit badges"))
    }

    @Test
    fun oneBadge_countIsSingular() {
        show(BadgesUiState.Ready(badges.take(1)))

        shownCount().assert(hasText("1 merit badge"))
    }

    @Test
    fun noMatches_showsMessageAndSearchField() {
        query.setTextAndPlaceCursorAtEnd("zoology")
        show(BadgesUiState.NoMatches)

        noMatchesMessage().assertIsDisplayed()
        searchField().assertIsDisplayed().assert(hasText("zoology"))
        list().assertDoesNotExist()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
    }

    @Test
    fun ready_hasNoNoMatchesMessage() {
        show(BadgesUiState.Ready(badges))

        noMatchesMessage().assertDoesNotExist()
    }

    // On a phone, a new field would lose focus and close the keyboard as the scout types.
    // Robolectric focuses the new field anyway, so this checks that it's the same field.
    @Test
    fun searchField_staysTheSameField_asMatchesComeAndGo() {
        show(BadgesUiState.Ready(badges))
        val field = searchField().fetchSemanticsNode().id

        uiState = BadgesUiState.NoMatches
        noMatchesMessage().assertIsDisplayed()
        assertEquals(field, searchField().fetchSemanticsNode().id)

        uiState = BadgesUiState.Ready(badges)
        row("Chess").assertIsDisplayed()
        assertEquals(field, searchField().fetchSemanticsNode().id)
    }

    // Screen readers announce a live region when its text changes. Compose announces only a
    // node that was already shown, so the count must stay the same node as matches come and
    // go, with only its text changing.
    @Test
    fun announcedCount_staysTheSamePoliteLiveRegion_asMatchesChange() {
        show(BadgesUiState.Ready(badges))
        val count = announcedCount()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .fetchSemanticsNode().id

        uiState = BadgesUiState.Ready(badges.take(1))
        pauseTyping()
        announcedCount().assert(hasText("1 merit badge"))
        assertEquals(count, announcedCount().fetchSemanticsNode().id)

        uiState = BadgesUiState.NoMatches
        pauseTyping()
        announcedCount().assert(hasText("No merit badges match your search."))
        assertEquals(count, announcedCount().fetchSemanticsNode().id)

        // As when the scout clears the search.
        uiState = BadgesUiState.Ready(badges)
        pauseTyping()
        announcedCount().assert(hasText("4 merit badges"))
        assertEquals(count, announcedCount().fetchSemanticsNode().id)
    }

    // TalkBack doesn't let new speech cut off a polite live region, so a count announced as
    // the scout types would hold back the keyboard's feedback on their next key.
    @Test
    fun announcedCount_changesOnlyOnceTypingPauses() {
        show(BadgesUiState.Ready(badges))
        composeTestRule.mainClock.autoAdvance = false

        val announcedSize = announcedCount().fetchSemanticsNode().size

        // Each keystroke filters the list straight away, as the ViewModel does.
        query.setTextAndPlaceCursorAtEnd("c")
        uiState = BadgesUiState.NoMatches
        waitFor(mostOfPause)
        // The screen shows the new count straight away.
        shownCount().assert(hasText("No merit badges match your search."))
        announcedCount().assert(hasText("4 merit badges"))
        // TalkBack announces a live region when even its size changes, so the announced count
        // mustn't change size with the shown one.
        assertEquals(announcedSize, announcedCount().fetchSemanticsNode().size)

        // A keystroke that leaves the same matches still restarts the wait.
        query.setTextAndPlaceCursorAtEnd("c ")
        waitFor(mostOfPause)
        // Longer than the pause since the count first changed, but the scout kept typing.
        announcedCount().assert(hasText("4 merit badges"))

        waitFor(restOfPause)
        announcedCount().assert(hasText("No merit badges match your search."))
    }

    // Deleting the search a key at a time is typing too, so it still waits for a pause.
    @Test
    fun deletingSearch_announcesCountOnceTypingPauses() {
        query.setTextAndPlaceCursorAtEnd("chess")
        show(BadgesUiState.Ready(badges.take(1)))
        composeTestRule.mainClock.autoAdvance = false

        query.setTextAndPlaceCursorAtEnd("")
        uiState = BadgesUiState.Ready(badges)
        waitFor(mostOfPause)
        announcedCount().assert(hasText("1 merit badge"))

        waitFor(restOfPause)
        announcedCount().assert(hasText("4 merit badges"))
    }

    // After Clear search the scout is about to type a new search. The count is announced
    // straight away, so it comes before TalkBack reads the search field rather than as the
    // scout starts typing.
    @Test
    fun clearSearch_announcesNewCountWithoutWaiting() {
        query.setTextAndPlaceCursorAtEnd("chess")
        show(BadgesUiState.Ready(badges.take(1)))
        composeTestRule.mainClock.autoAdvance = false

        clearButton().performClick()
        catchUp()
        // The ViewModel lists every badge again a moment later.
        uiState = BadgesUiState.Ready(badges)
        catchUp()

        announcedCount().assert(hasText("4 merit badges"))
    }

    // A count the scout typed but hadn't paused to hear isn't announced once they clear it.
    @Test
    fun clearSearch_beforeCountIsAnnounced_skipsClearedCount() {
        query.setTextAndPlaceCursorAtEnd("chess")
        show(BadgesUiState.Ready(badges.take(1)))
        composeTestRule.mainClock.autoAdvance = false
        query.setTextAndPlaceCursorAtEnd("chessz")
        uiState = BadgesUiState.NoMatches
        catchUp()

        clearButton().performClick()
        catchUp()
        announcedCount().assert(hasText("1 merit badge"))

        uiState = BadgesUiState.Ready(badges)
        catchUp()
        announcedCount().assert(hasText("4 merit badges"))
    }

    // Only the count that replaces the cleared one skips the wait.
    @Test
    fun afterClearSearch_deletingSearchStillWaitsForPause() {
        query.setTextAndPlaceCursorAtEnd("chess")
        show(BadgesUiState.Ready(badges.take(1)))
        clearButton().performClick()
        uiState = BadgesUiState.Ready(badges)
        announcedCount().assert(hasText("4 merit badges"))
        composeTestRule.mainClock.autoAdvance = false

        query.setTextAndPlaceCursorAtEnd("c")
        uiState = BadgesUiState.Ready(badges.take(3))
        pauseTyping()
        announcedCount().assert(hasText("3 merit badges"))
        query.setTextAndPlaceCursorAtEnd("")
        uiState = BadgesUiState.Ready(badges)
        waitFor(mostOfPause)

        announcedCount().assert(hasText("3 merit badges"))
    }

    // Once the catalog loads, the count is new on the screen, so screen readers don't
    // announce it, and there's no reason to wait.
    @Test
    fun count_appearsWithoutWaiting_whenBadgesLoad() {
        show(BadgesUiState.Loading)
        composeTestRule.mainClock.autoAdvance = false

        uiState = BadgesUiState.Ready(badges)
        catchUp()

        announcedCount().assert(hasText("4 merit badges"))
    }

    @Test
    fun newMatches_areShownFromTheTop() {
        show(BadgesUiState.Ready(many))
        list().performScrollToNode(hasText("Badge 150"))

        // Badge 1, Badge 10 to 19, then Badge 100 to 199, which include the rows on screen.
        uiState = BadgesUiState.Ready(many.filter { it.name.startsWith("Badge 1") })

        row("Badge 1").assertIsDisplayed()
    }

    @Test
    fun sameBadges_keepTheirScrollPosition() {
        show(BadgesUiState.Ready(many))
        list().performScrollToNode(hasText("Badge 200"))

        // As when the scout comes back from a badge they started.
        uiState = BadgesUiState.Ready(many.map { it.copy(status = BadgeStatus.InProgress) })

        row("Badge 200").assertIsDisplayed()
        row("Badge 1").assertDoesNotExist()
    }
}
