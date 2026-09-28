package io.github.bryancassell.bluecard.ui.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var name: String? = null
    private var unitNumber: String? = null
    private var saves = 0

    private val filledIn = OnboardingUiState(name = "Alex Scout", unitNumber = "123")

    /** Records what the screen asks of the on-screen keyboard. */
    private val keyboard = object : SoftwareKeyboardController {
        var hides = 0

        override fun show() = Unit

        override fun hide() {
            hides++
        }
    }

    private fun show(uiState: OnboardingUiState) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                OnboardingScreen(
                    uiState = uiState,
                    onNameChange = { name = it },
                    onUnitNumberChange = { unitNumber = it },
                    onSave = { saves++ }
                )
            }
        }
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
        show(filledIn)

        field("Name").assertTextContains("Alex Scout")
        field("Unit number").assertTextContains("123")
        saveButton().assertIsEnabled()
    }

    @Test
    fun saving_disablesFieldsAndSave() {
        show(filledIn.copy(saveStatus = SaveStatus.Saving))

        field("Name").assertIsNotEnabled()
        field("Unit number").assertIsNotEnabled()
        saveButton().assertIsNotEnabled()
    }

    @Test
    fun saved_disablesFieldsAndSave() {
        show(filledIn.copy(saveStatus = SaveStatus.Saved))

        field("Name").assertIsNotEnabled()
        field("Unit number").assertIsNotEnabled()
        saveButton().assertIsNotEnabled()
        saveFailedMessage().assertDoesNotExist()
    }

    @Test
    fun failed_showsMessageAndAllowsRetry() {
        show(filledIn.copy(saveStatus = SaveStatus.Failed))

        saveFailedMessage().assertExists()
        field("Name").assertIsEnabled()
        field("Unit number").assertIsEnabled()
        saveButton().assertIsEnabled()
    }

    @Test
    fun typingName_reportsIt() {
        show(OnboardingUiState())

        field("Name").performTextInput("Alex")

        assertEquals("Alex", name)
    }

    @Test
    fun typingUnitNumber_reportsIt() {
        show(OnboardingUiState())

        field("Unit number").performTextInput("123")

        assertEquals("123", unitNumber)
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
        show(filledIn)

        saveButton().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun keyboardDone_onUnitNumber_hidesKeyboardAndSaves() {
        show(filledIn)

        field("Unit number").performImeAction()

        assertEquals(1, keyboard.hides)
        assertEquals(1, saves)
    }
}
