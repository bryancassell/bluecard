package io.github.bryancassell.bluecard.ui.badge

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
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.BackPresses
import io.github.bryancassell.bluecard.testing.paragraphDirection
import io.github.bryancassell.bluecard.ui.SaveFailure
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class EditCounselorScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val name = TextFieldState()
    private val phone = TextFieldState()
    private val email = TextFieldState()
    private var saves = 0
    private var closes = 0
    private var discards = 0
    private val back = BackPresses()
    private val saveFailuresShown = mutableListOf<SaveFailure>()

    private val ready = EditCounselorUiState.Ready(badgeName = "Camping", changed = false)

    private fun show(uiState: EditCounselorUiState) {
        composeTestRule.setContent {
            back.Content {
                EditCounselorScreen(
                    uiState = uiState,
                    name = name,
                    phone = phone,
                    email = email,
                    onSave = { saves++ },
                    onSaved = { closes++ },
                    onDiscard = { discards++ },
                    onSaveFailureShown = { saveFailuresShown += it }
                )
            }
        }
    }

    private fun field(label: String) =
        composeTestRule.onNode(hasSetTextAction() and hasText(label)).performScrollTo()

    private fun saveButton() = composeTestRule.onNodeWithText("Save").performScrollTo()

    private fun hasMaxTextLength(length: Int) =
        SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, length)

    @Test
    fun loading_showsProgressOnly() {
        show(EditCounselorUiState.Loading)

        composeTestRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Counselor").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(EditCounselorUiState.LoadFailed)

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Counselor").assertDoesNotExist()
    }

    @Test
    fun unavailable_showsMessageOnly() {
        show(EditCounselorUiState.Unavailable)

        composeTestRule
            .onNodeWithText("This badge's requirements aren't in this version of BlueCard.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Counselor").assertDoesNotExist()
    }

    @Test
    fun ready_showsBadgeAndFields() {
        show(ready)

        composeTestRule.onNodeWithText("Camping").assertIsDisplayed()
        composeTestRule.onNodeWithText("Counselor").assert(isHeading()).assertIsDisplayed()
        field("Name").assertIsDisplayed()
        field("Phone").assertIsDisplayed()
        field("Email").assertIsDisplayed()
    }

    @Test
    fun fields_areEditable() {
        show(ready)

        field("Name").performTextInput("Pat Lee")
        field("Phone").performTextInput("555-0100")
        field("Email").performTextInput("pat@example.com")

        assertEquals("Pat Lee", name.text.toString())
        assertEquals("555-0100", phone.text.toString())
        assertEquals("pat@example.com", email.text.toString())
    }

    @Test
    fun next_movesToTheNextField() {
        show(ready)

        field("Name").performClick().performImeAction()
        field("Phone").assertIsFocused().performImeAction()
        field("Email").assertIsFocused()
    }

    @Test
    fun keyboardAction_isNextUntilTheLastField_thenDone() {
        show(ready)

        field("Name").assert(hasImeAction(ImeAction.Next))
        field("Phone").assert(hasImeAction(ImeAction.Next))
        field("Email").assert(hasImeAction(ImeAction.Done))
    }

    // The layout is left-to-right, like the English strings, but a name typed in Persian reads
    // right-to-left, with its final period at its end.
    @Test
    fun nameTypedInPersian_readsRightToLeft() {
        show(ready)

        field("Name").performTextInput("علی رضایی.")

        assertEquals(ResolvedTextDirection.Rtl, field("Name").paragraphDirection())
    }

    @Test
    fun fields_trimTextPastTheirLimits() {
        show(ready)

        // As when pasting.
        field("Name").performTextInput("a".repeat(110))
        field("Phone").performTextInput("5".repeat(60))
        field("Email").performTextInput("e".repeat(260))

        assertEquals(100, name.text.length)
        assertEquals(50, phone.text.length)
        assertEquals(254, email.text.length)
        field("Name").assert(hasMaxTextLength(100))
        field("Phone").assert(hasMaxTextLength(50))
        field("Email").assert(hasMaxTextLength(254))
    }

    @Test
    fun pastedLineBreaks_becomeSpaces() {
        show(ready)

        field("Name").performTextInput("Pat Lee\nTroop 12")
        field("Phone").performTextInput("555-0100\r\nmobile")
        field("Email").performTextInput("pat@example.com\n")

        assertEquals("Pat Lee Troop 12", name.text.toString())
        assertEquals("555-0100 mobile", phone.text.toString())
        assertEquals("pat@example.com ", email.text.toString())
    }

    @Test
    fun unchanged_cannotBeSaved() {
        show(ready)

        saveButton().assertIsNotEnabled()
    }

    @Test
    fun changed_isSaved() {
        show(ready.copy(changed = true))

        saveButton().assertIsEnabled().performClick()

        assertEquals(1, saves)
        assertEquals(0, closes)
    }

    @Test
    fun back_withChanges_asksBeforeDiscardingThem() {
        show(ready.copy(changed = true))

        back.press(composeTestRule)
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performClick()

        assertEquals(1, discards)
        assertEquals(0, back.closes)
    }

    @Test
    fun back_withoutChanges_closesThePage() {
        show(ready)

        back.press(composeTestRule)

        assertEquals(1, back.closes)
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun save_closesTheKeyboard() {
        show(ready.copy(changed = true))
        field("Email").performClick().assertIsFocused()

        saveButton().performClick()

        field("Email").assertIsNotFocused()
    }

    @Test
    fun saved_closesThePageOnce() {
        var uiState by mutableStateOf<EditCounselorUiState>(ready.copy(changed = true))
        composeTestRule.setContent {
            back.Content {
                EditCounselorScreen(
                    uiState = uiState,
                    name = name,
                    phone = phone,
                    email = email,
                    onSave = {},
                    onSaved = { closes++ },
                    onDiscard = {},
                    onSaveFailureShown = {}
                )
            }
        }
        assertEquals(0, closes)

        // Saved before the saved counselor arrives from the database, which then changes the
        // state again without closing the page again.
        uiState = ready.copy(changed = true, saved = true)
        composeTestRule.waitForIdle()
        uiState = ready.copy(changed = false, saved = true)
        composeTestRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun savedAgain_afterClosing_closesThePageAgain() {
        // As when the page is opened again before its ViewModel is cleared.
        var uiState by mutableStateOf<EditCounselorUiState>(ready.copy(saved = true))
        composeTestRule.setContent {
            back.Content {
                EditCounselorScreen(
                    uiState = uiState,
                    name = name,
                    phone = phone,
                    email = email,
                    onSave = {},
                    onSaved = { closes++ },
                    onDiscard = {},
                    onSaveFailureShown = {}
                )
            }
        }
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
        show(ready.copy(changed = true, saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<SaveFailure>(), saveFailuresShown)
        assertEquals(0, closes)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }
}
