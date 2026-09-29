package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
class LineBreaksAsSpacesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val state = TextFieldState()

    @Before
    fun showField() {
        composeTestRule.setContent {
            BasicTextField(
                state = state,
                inputTransformation = LineBreaksAsSpaces,
                lineLimits = TextFieldLineLimits.SingleLine
            )
        }
    }

    // Each edit below is as when pasting.
    private fun field() = composeTestRule.onNode(hasSetTextAction())

    @Test
    fun textWithoutLineBreaks_isKept() {
        field().performTextInput("Alex Scout")

        assertEquals("Alex Scout", state.text.toString())
    }

    @Test
    fun lineFeed_becomesSpace() {
        field().performTextInput("Alex\nScout")

        assertEquals("Alex Scout", state.text.toString())
    }

    @Test
    fun carriageReturnAndLineFeed_becomeOneSpace() {
        field().performTextInput("Alex\r\nScout")

        assertEquals("Alex Scout", state.text.toString())
    }

    @Test
    fun carriageReturn_becomesSpace() {
        field().performTextInput("Alex\rScout")

        assertEquals("Alex Scout", state.text.toString())
    }

    @Test
    fun eachOfSeveralLineBreaks_becomesSpace() {
        field().performTextInput("a\n\nb\r\n\r\nc\r\rd\n\re\n")

        assertEquals("a  b  c  d  e ", state.text.toString())
    }

    @Test
    fun otherUnicodeLineBreaks_becomeSpaces() {
        // Vertical tab, form feed, next line, line separator and paragraph separator.
        field().performTextInput("a\u000Bb\u000Cc\u0085d\u2028e\u2029f")

        assertEquals("a b c d e f", state.text.toString())
    }

    @Test
    fun editInTheMiddle_keepsCursorAfterIt() {
        field().performTextInput("AlexScout")
        field().performTextInputSelection(TextRange(4))

        field().performTextInput("\r\nB.\r\nJ. ")

        assertEquals("Alex B. J. Scout", state.text.toString())
        assertEquals(TextRange(11), state.selection)
    }
}
