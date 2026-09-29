package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LineBreaksToSpacesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = TextFieldState()

    @Before
    fun showField() {
        composeTestRule.setContent {
            BasicTextField(state = state, inputTransformation = LineBreaksToSpaces)
        }
    }

    private fun field() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun textWithoutLineBreaks_isKept() {
        field().performTextInput("Pat Lee")

        assertEquals("Pat Lee", state.text.toString())
    }

    @Test
    fun pastedLineBreak_becomesASpace() {
        field().performTextInput("Pat Lee\nTroop 12\nMerit badge counselor")

        assertEquals("Pat Lee Troop 12 Merit badge counselor", state.text.toString())
    }

    @Test
    fun windowsLineBreak_becomesOneSpace() {
        field().performTextInput("555-0100\r\nmobile")

        assertEquals("555-0100 mobile", state.text.toString())
    }

    @Test
    fun otherLineBreaks_becomeSpaces() {
        field().performTextInput("a\rb\u0085c d e")

        assertEquals("a b c d e", state.text.toString())
    }

    @Test
    fun lineBreakAddedToExistingText_becomesASpace() {
        field().performTextInput("Pat")

        field().performTextInput("\nLee")

        assertEquals("Pat Lee", state.text.toString())
    }
}
