package io.github.bryancassell.bluecard.ui.onboarding

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.paragraphDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val name = TextFieldState()
    private val unitNumber = TextFieldState()
    private var saves = 0

    /** Records what the screen asks of the on-screen keyboard. */
    private val keyboard = object : SoftwareKeyboardController {
        var hides = 0

        override fun show() = Unit

        override fun hide() {
            hides++
        }
    }

    // The view that hosts the screen, which connects the keyboard to the focused field.
    private lateinit var view: View

    private fun show(uiState: OnboardingUiState) {
        composeTestRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                OnboardingScreen(
                    uiState = uiState,
                    name = name,
                    unitNumber = unitNumber,
                    onSave = { saves++ }
                )
            }
        }
    }

    private fun showFilledIn(saveStatus: SaveStatus = SaveStatus.Editing) {
        name.setTextAndPlaceCursorAtEnd("Alex Scout")
        unitNumber.setTextAndPlaceCursorAtEnd("123")
        show(OnboardingUiState(saveStatus, isComplete = true))
    }

    // Matches on EditableText, not the SetText action, which disabled fields don't have.
    private fun field(label: String) = composeTestRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(label)
    )

    private fun saveButton() = composeTestRule.onNodeWithText("Get started")

    private fun saveFailedMessage() = composeTestRule.onNodeWithText("Couldn't save. Try again.")

    @Test
    fun empty_showsFieldsAndDisablesSave() {
        show(OnboardingUiState())

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertExists()
        field("Name").assertIsEnabled()
        field("Unit number").assertIsEnabled()
        saveButton().assertIsNotEnabled()
        saveFailedMessage().assertDoesNotExist()
    }

    @Test
    fun filledIn_showsValuesAndEnablesSave() {
        showFilledIn()

        field("Name").assertTextContains("Alex Scout")
        field("Unit number").assertTextContains("123")
        saveButton().assertIsEnabled()
    }

    @Test
    fun saving_disablesFieldsAndSave() {
        showFilledIn(SaveStatus.Saving)

        field("Name").assertIsNotEnabled()
        field("Unit number").assertIsNotEnabled()
        saveButton().assertIsNotEnabled()
    }

    @Test
    fun saved_disablesFieldsAndSave() {
        showFilledIn(SaveStatus.Saved)

        field("Name").assertIsNotEnabled()
        field("Unit number").assertIsNotEnabled()
        saveButton().assertIsNotEnabled()
        saveFailedMessage().assertDoesNotExist()
    }

    @Test
    fun failed_showsMessageAndAllowsRetry() {
        showFilledIn(SaveStatus.Failed)

        saveFailedMessage().assertExists()
        field("Name").assertIsEnabled()
        field("Unit number").assertIsEnabled()
        saveButton().assertIsEnabled()
    }

    @Test
    fun typingName_editsName() {
        show(OnboardingUiState())

        field("Name").performTextInput("Alex")

        assertEquals("Alex", name.text.toString())
        assertEquals("", unitNumber.text.toString())
    }

    @Test
    fun typingUnitNumber_editsUnitNumber() {
        show(OnboardingUiState())

        field("Unit number").performTextInput("123")

        assertEquals("123", unitNumber.text.toString())
        assertEquals("", name.text.toString())
    }

    @Test
    fun name_trimsTextPast100Characters() {
        show(OnboardingUiState())
        field("Name").performTextInput("a".repeat(90))

        // As when pasting.
        field("Name").performTextInput("b".repeat(20))

        assertEquals("a".repeat(90) + "b".repeat(10), name.text.toString())
        field("Name").assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 100))
    }

    @Test
    fun unitNumber_trimsTextPast20Characters() {
        show(OnboardingUiState())
        field("Unit number").performTextInput("1".repeat(15))

        // As when pasting.
        field("Unit number").performTextInput("2".repeat(10))

        assertEquals("1".repeat(15) + "2".repeat(5), unitNumber.text.toString())
        field("Unit number")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 20))
    }

    // A single-line field shows a pasted line break as a space, so it's saved as one.
    @Test
    fun name_pastedLineBreak_becomesSpace() {
        show(OnboardingUiState())

        field("Name").performTextInput("Alex\nScout")

        assertEquals("Alex Scout", name.text.toString())
    }

    @Test
    fun unitNumber_pastedLineBreak_becomesSpace() {
        show(OnboardingUiState())

        field("Unit number").performTextInput("Troop\r\n123")

        assertEquals("Troop 123", unitNumber.text.toString())
    }

    // The length limit finds what a paste changed before its line breaks become spaces.
    // Otherwise a pasted line break, a space by then, would look like the space already after
    // the cursor, and the limit would cut that space instead of the paste's end.
    @Test
    fun name_longPasteStartingWithLineBreak_keepsTheWordsAfterItApart() {
        show(OnboardingUiState())
        field("Name").performTextInput("A".repeat(89) + " Scout")
        field("Name").performTextInputSelection(TextRange(89))

        field("Name").performTextInput("\nJunior Assistant")

        assertEquals("A".repeat(89) + " Juni Scout", name.text.toString())
    }

    @Test
    fun unitNumber_longPasteStartingWithLineBreak_keepsTheWordsAfterItApart() {
        show(OnboardingUiState())
        field("Unit number").performTextInput("1".repeat(14) + " B")
        field("Unit number").performTextInputSelection(TextRange(14))

        field("Unit number").performTextInput("\n234567")

        assertEquals("1".repeat(14) + " 234 B", unitNumber.text.toString())
    }

    @Test
    fun name_capitalizesWords() {
        show(OnboardingUiState())
        field("Name").performClick()

        val editorInfo = EditorInfo()
        composeTestRule.runOnIdle { view.onCreateInputConnection(editorInfo) }

        assertEquals(
            InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
    }

    @Test
    fun keyboardNext_onName_movesToUnitNumber() {
        show(OnboardingUiState())

        field("Name").performClick()
        field("Name").performImeAction()

        field("Unit number").assertIsFocused()
    }

    @Test
    fun clickingSave_saves() {
        showFilledIn()

        saveButton().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun keyboardDone_onUnitNumber_hidesKeyboardAndSaves() {
        showFilledIn()

        field("Unit number").performImeAction()

        assertEquals(1, keyboard.hides)
        assertEquals(1, saves)
    }

    // The layout is left-to-right, like the English strings, but a name typed in Persian
    // reads right-to-left, with its final period at its end, on the left.
    @Test
    fun nameTypedInPersian_readsRightToLeft() {
        show(OnboardingUiState())

        field("Name").performTextInput("علی رضایی.")

        assertEquals(ResolvedTextDirection.Rtl, field("Name").paragraphDirection())
    }

    @Test
    fun unitNumberTypedInPersian_readsRightToLeft() {
        show(OnboardingUiState())

        field("Unit number").performTextInput("گروه ۱۲۳.")

        assertEquals(ResolvedTextDirection.Rtl, field("Unit number").paragraphDirection())
    }
}
