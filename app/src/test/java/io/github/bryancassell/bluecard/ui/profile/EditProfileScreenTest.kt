package io.github.bryancassell.bluecard.ui.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.isPaneTitledWithItsText
import io.github.bryancassell.bluecard.ui.SaveFailure
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One test per UI state and interaction, with fixed UI state. The fields' own behavior, such as
 * their length limits, is checked through Onboarding, which shares them (OnboardingScreenTest).
 */
@RunWith(AndroidJUnit4::class)
class EditProfileScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val name = TextFieldState()
    private val unitNumber = TextFieldState()
    private var saves = 0
    private var closes = 0
    private val saveFailuresShown = mutableListOf<SaveFailure>()

    private val ready = EditProfileUiState.Ready(canSave = false)

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<EditProfileUiState>(ready)

    private fun show(state: EditProfileUiState) {
        uiState = state
        composeTestRule.setContent {
            EditProfileScreen(
                uiState = uiState,
                name = name,
                unitNumber = unitNumber,
                onSave = { saves++ },
                onSaved = { closes++ },
                onSaveFailureShown = { saveFailuresShown += it }
            )
        }
    }

    private fun field(label: String) =
        composeTestRule.onNode(hasSetTextAction() and hasText(label)).performScrollTo()

    private fun saveButton() = composeTestRule.onNodeWithText("Save").performScrollTo()

    @Test
    fun loading_showsProgressOnly() {
        show(EditProfileUiState.Loading)

        composeTestRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Name and unit").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(EditProfileUiState.LoadFailed)

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assert(isPaneTitledWithItsText).assertIsDisplayed()
        composeTestRule.onNodeWithText("Name and unit").assertDoesNotExist()
    }

    @Test
    fun ready_showsHeadingAndRequiredFields() {
        show(ready)

        composeTestRule.onNodeWithText("Name and unit").assert(isHeading()).assertIsDisplayed()
        field("Name").assert(hasText("Required")).assertIsDisplayed()
        field("Unit number").assert(hasText("Required")).assertIsDisplayed()
    }

    @Test
    fun fields_areEditable() {
        show(ready)

        field("Name").performTextInput("Sam Scout")
        field("Unit number").performTextInput("Crew 7")

        assertEquals("Sam Scout", name.text.toString())
        assertEquals("Crew 7", unitNumber.text.toString())
    }

    // Unlike Onboarding's, where Done saves: here Save also closes the page, so the scout
    // decides when.
    @Test
    fun next_movesToUnitNumber_whereDoneDoesntSave() {
        show(ready.copy(canSave = true))

        field("Name").assert(hasImeAction(ImeAction.Next)).performClick().performImeAction()
        field("Unit number").assert(hasImeAction(ImeAction.Done)).assertIsFocused()
            .performImeAction()

        assertEquals(0, saves)
    }

    @Test
    fun cantSave_disablesSave() {
        show(ready)

        saveButton().assertIsNotEnabled()
    }

    @Test
    fun canSave_isSaved() {
        show(ready.copy(canSave = true))

        saveButton().assertIsEnabled().performClick()

        assertEquals(1, saves)
        assertEquals(0, closes)
    }

    // The tests' view isn't in touch mode, as a phone's is while the scout taps it, so Android
    // gives focus to the first field once it's cleared. The field the scout typed in last
    // shows that Save cleared it.
    @Test
    fun save_closesTheKeyboard() {
        show(ready.copy(canSave = true))
        field("Unit number").performClick().assertIsFocused()

        saveButton().performClick()

        field("Unit number").assertIsNotFocused()
    }

    @Test
    fun saved_closesThePageOnce() {
        show(ready.copy(canSave = true))
        assertEquals(0, closes)

        // Saved before the saved profile arrives, which then changes the state again without
        // closing the page again.
        uiState = ready.copy(canSave = true, saved = true)
        composeTestRule.waitForIdle()
        uiState = ready.copy(canSave = false, saved = true)
        composeTestRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun savedAgain_afterClosing_closesThePageAgain() {
        // As when the page is opened again before its ViewModel is cleared.
        show(ready.copy(saved = true))
        composeTestRule.waitForIdle()

        uiState = ready.copy(saved = false)
        composeTestRule.waitForIdle()
        uiState = ready.copy(saved = true)
        composeTestRule.waitForIdle()

        assertEquals(2, closes)
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        val failure = SaveFailure()
        show(ready.copy(canSave = true, saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<SaveFailure>(), saveFailuresShown)
        assertEquals(0, closes)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }
}
