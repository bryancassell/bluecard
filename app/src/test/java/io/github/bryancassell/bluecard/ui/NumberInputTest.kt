package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = TextFieldState()

    @Before
    fun showField() {
        composeTestRule.setContent {
            BasicTextField(state = state, inputTransformation = NumberInput)
        }
    }

    private fun type(text: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    @Test
    fun keepsDigits() {
        type("30")

        assertEquals("30", state.text.toString())
    }

    @Test
    fun keepsADecimalPoint() {
        type("2.5")

        assertEquals("2.5", state.text.toString())
    }

    @Test
    fun keepsADecimalComma() {
        type("2,5")

        assertEquals("2,5", state.text.toString())
    }

    @Test
    fun rejectsASecondSeparator() {
        type("2.5")

        type(",")

        assertEquals("2.5", state.text.toString())
    }

    @Test
    fun rejectsAnEditWithAnythingElse() {
        type("3")

        // As when pasting.
        type("0 min")
        type("-")

        assertEquals("3", state.text.toString())
    }

    @Test
    fun keepsDigitsOfOtherScripts() {
        type("۳۰")

        assertEquals("۳۰", state.text.toString())
    }

    @Test
    fun asksForADecimalKeyboard() {
        assertEquals(KeyboardType.Decimal, NumberInput.keyboardOptions.keyboardType)
    }
}
