package io.github.bryancassell.bluecard.ui.onboarding

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
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

    private fun show(uiState: OnboardingUiState) {
        composeTestRule.setContent {
            OnboardingScreen(
                uiState = uiState,
                onNameChange = { name = it },
                onUnitNumberChange = { unitNumber = it },
                onSave = { saves++ }
            )
        }
    }

    private fun field(label: String) = composeTestRule.onNode(hasSetTextAction() and hasText(label))

    private fun saveButton() = composeTestRule.onNodeWithText("Get started")

    @Test
    fun empty_showsFieldsAndDisablesSave() {
        show(OnboardingUiState())

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertExists()
        field("Name").assertExists()
        field("Unit number").assertExists()
        saveButton().assertIsNotEnabled()
    }

    @Test
    fun filledIn_showsValuesAndEnablesSave() {
        show(OnboardingUiState(name = "Alex Scout", unitNumber = "123"))

        field("Name").assertTextContains("Alex Scout")
        field("Unit number").assertTextContains("123")
        saveButton().assertIsEnabled()
    }

    @Test
    fun saving_disablesSave() {
        show(OnboardingUiState(name = "Alex Scout", unitNumber = "123", isSaving = true))

        saveButton().assertIsNotEnabled()
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
    fun clickingSave_saves() {
        show(OnboardingUiState(name = "Alex Scout", unitNumber = "123"))

        saveButton().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun keyboardDone_onUnitNumber_saves() {
        show(OnboardingUiState(name = "Alex Scout", unitNumber = "123"))

        field("Unit number").performImeAction()

        assertEquals(1, saves)
    }
}
