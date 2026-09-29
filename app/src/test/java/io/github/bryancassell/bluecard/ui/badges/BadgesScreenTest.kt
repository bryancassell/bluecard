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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import org.junit.Assert.assertEquals
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
        BadgeListItem("chess", "Chess", eagle = null, BadgeStatus.InProgress),
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
        var hides = 0

        override fun show() = Unit

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

    private fun noMatchesMessage() =
        composeTestRule.onNodeWithText("No merit badges match your search.")

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
    fun searchField_acceptsAtMost100Characters() {
        show(BadgesUiState.Ready(badges))
        val longest = "a".repeat(100)
        searchField().performTextInput(longest)

        searchField().performTextInput("b")

        assertEquals(longest, query.text.toString())
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
