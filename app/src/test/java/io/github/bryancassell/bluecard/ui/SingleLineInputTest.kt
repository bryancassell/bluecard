package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
class SingleLineInputTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = TextFieldState()

    @Before
    fun showField() {
        composeTestRule.setContent {
            BasicTextField(
                state = state,
                inputTransformation = singleLineInput(maxLength = 6),
                lineLimits = TextFieldLineLimits.SingleLine
            )
        }
    }

    // Each edit below is as when pasting.
    private fun field() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun pasteWithLineBreaksPastTheLimit_isCutAndItsLineBreaksBecomeSpaces() {
        field().performTextInput("ab\ncdefgh")

        assertEquals("ab cde", state.text.toString())
    }

    @Test
    fun limit_cutsThePasteBeforeItsLineBreaksBecomeSpaces() {
        // The limit counts "\r\n" as two characters, so it keeps "abcd\r\n", which becomes
        // "abcd ". The other way round, "abcd ef" would be cut to "abcd e".
        field().performTextInput("abcd\r\nef")

        assertEquals("abcd ", state.text.toString())
    }

    @Test
    fun pasteInTheMiddle_isCutAndKeepsCursorAfterIt() {
        field().performTextInput("abcd")
        field().performTextInputSelection(TextRange(2))

        // Only "x\n" fits, and its line break becomes a space.
        field().performTextInput("x\ny\nz")

        assertEquals("abx cd", state.text.toString())
        assertEquals(TextRange(4), state.selection)
    }

    @Test
    fun field_tellsAccessibilityServicesTheLimit() {
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 6))
    }
}
