package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextLengthLimitTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = TextFieldState()

    @Before
    fun showField() {
        composeTestRule.setContent {
            BasicTextField(state = state, inputTransformation = TextLengthLimit(maxLength = 5))
        }
    }

    private fun field() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun editWithinLimit_isKept() {
        field().performTextInput("abcde")

        assertEquals("abcde", state.text.toString())
    }

    @Test
    fun editPastLimit_isCutOffAtIt() {
        field().performTextInput("abc")

        // As when pasting.
        field().performTextInput("defgh")

        assertEquals("abcde", state.text.toString())
    }

    @Test
    fun editInTheMiddle_keepsTheTextAfterIt() {
        field().performTextInput("abcd")
        field().performTextInputSelection(TextRange(1))

        field().performTextInput("XYZ")

        assertEquals("aXbcd", state.text.toString())
    }

    @Test
    fun typingIntoAFullField_changesNothing() {
        field().performTextInput("abcde")
        field().performTextInputSelection(TextRange(2))

        field().performTextInput("X")

        assertEquals("abcde", state.text.toString())
    }

    @Test
    fun characterMadeOfTwoUnits_isNotSplitAtTheLimit() {
        field().performTextInput("abcd")

        // An emoji is two UTF-16 units, so only half of it would fit.
        field().performTextInput("\uD83D\uDE00")

        assertEquals("abcd", state.text.toString())
    }

    @Test
    fun field_tellsAccessibilityServicesTheLimit() {
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 5))
    }
}
