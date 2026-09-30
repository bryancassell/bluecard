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
import androidx.compose.ui.test.performTextReplacement
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
    fun pasteStartingLikeTheTextAfterTheCursor_keepsThatText() {
        field().performTextInput("a b")
        field().performTextInputSelection(TextRange(1))

        // Starts with a space, as does the text after the cursor.
        field().performTextInput(" xyz")

        assertEquals("a x b", state.text.toString())
    }

    @Test
    fun replacementStartingLikeTheTextFromTheSelectionOn_isCutAtItsEnd() {
        field().performTextInput("a c")
        field().performTextInputSelection(TextRange(0, 1))

        // Starts with the selected "a" and the space after it, and ends with "a", so the
        // same text could come from adding " xa", "a x" or "xa " instead.
        field().performTextInput("a xa")

        assertEquals("a x c", state.text.toString())
    }

    @Test
    fun replacementOfABackwardsSelection_isCutAtItsEnd() {
        // Selected from right to left, as with Shift+Left. (Set in code, since the semantics
        // action for selecting text always selects left to right.)
        state.edit {
            append("a c")
            selection = TextRange(1, 0)
        }

        field().performTextInput("a xa")

        assertEquals("a x c", state.text.toString())
    }

    @Test
    fun replacementEndingWithTheSelectedText_isCutAtItsEnd() {
        field().performTextInput("abc")
        field().performTextInputSelection(TextRange(1, 3))

        field().performTextInput("xyzbc")

        assertEquals("axyzb", state.text.toString())
    }

    @Test
    fun replacementEndingLikeTheSelectedText_isCutAtItsEnd() {
        field().performTextInput("abc")
        field().performTextInputSelection(TextRange(1, 3))

        // Ends with "c", as does the selected "bc".
        field().performTextInput("wxyzc")

        assertEquals("awxyz", state.text.toString())
    }

    @Test
    fun deletionFromTextAlreadyPastTheLimit_cutsItAtTheLimit() {
        // Text set in code isn't limited. (Moving the cursor would cut it, so that's set in
        // code too.)
        state.edit {
            append("aaaaaaa")
            selection = TextRange(3)
        }

        // Takes out one "a", so the three before the cursor and the four after it still start
        // and end the text, but overlap.
        field().performTextReplacement("aaaaaa")

        assertEquals("aaaaa", state.text.toString())
    }

    @Test
    fun editBeforeTheCursor_keepsTheTextBetweenThem() {
        field().performTextInput("a cd")

        // Changes the word before the cursor, as a keyboard's autocorrect can.
        field().performTextReplacement("abb cd")

        assertEquals("ab cd", state.text.toString())
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
    fun emojiMadeOfSeveralCodePoints_isNotSplitAtTheLimit() {
        field().performTextInput("abc")

        // A flag (two regional indicators, four UTF-16 units) and a thumbs-up with a skin
        // tone (two code points, four units): neither fits, and half of one would be wrong.
        field().performTextInput("\uD83C\uDDFA\uD83C\uDDF8")
        field().performTextInput("\uD83D\uDC4D\uD83C\uDFFD")

        assertEquals("abc", state.text.toString())
    }

    @Test
    fun replacementEndingLikeTheOldText_neverLeavesHalfAnEmoji() {
        // Ends with U+1F525, whose second UTF-16 unit is the same as U+1F925's.
        field().performTextInput("aaa\uD83D\uDD25")
        field().performTextInputSelection(TextRange(0, 5))

        field().performTextInput("bbbbbb\uD83E\uDD25")

        assertEquals("bbbbb", state.text.toString())
    }

    @Test
    fun accentOnTheLastLetterOfAFullField_changesNothing() {
        field().performTextInput("abcde")

        // A combining acute accent, which would make "e" into "é".
        field().performTextInput("\u0301")

        assertEquals("abcde", state.text.toString())
    }

    @Test
    fun field_tellsAccessibilityServicesTheLimit() {
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.MaxTextLength, 5))
    }
}
