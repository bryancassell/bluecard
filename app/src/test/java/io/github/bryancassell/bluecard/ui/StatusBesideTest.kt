package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Whether a status fits beside lines of text without breaking a word (#307), at widths measured
 * to the pixel: native graphics measure text as a phone does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusBesideTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val style = TextStyle(fontSize = 16.sp)

    /**
     * Whether "Done" fits beside [lines] in the width [width] gives, from a measurer for the
     * same density and fonts.
     */
    private fun fits(vararg lines: String, width: TextMeasurer.() -> Int): Boolean {
        var fits: Boolean? = null
        composeTestRule.setContent {
            fits = statusFitsBeside(
                status = "Done",
                statusStyle = style,
                lines = lines.map { it to style },
                width = rememberTextMeasurer().width()
            )
        }
        composeTestRule.waitForIdle()
        return fits!!
    }

    /** The width of [text] on one line. */
    private fun TextMeasurer.widthOf(text: String) = measure(text, style).size.width

    @Test
    fun aWordJustWideEnoughBesideTheStatus_fits() {
        assertTrue(fits("Swimming") { widthOf("Done") + widthOf("Swimming") })
    }

    @Test
    fun aWordAPixelTooWideBesideTheStatus_doesnt() {
        assertFalse(fits("Swimming") { widthOf("Done") + widthOf("Swimming") - 1 })
    }

    @Test
    fun linesThatWrapOnlyBetweenWords_fit() {
        assertTrue(
            fits("Citizenship in the Community") {
                widthOf("Done") + maxOf(widthOf("Citizenship"), widthOf("Community"))
            }
        )
    }

    // Compose's narrowest width for it ends a word at the hyphen, but Android doesn't break there
    // without hyphenation, which the app leaves off, so a narrower line breaks "required".
    @Test
    fun aHyphenatedWord_needsItsWholeWidth() {
        assertFalse(fits("Eagle-required") { widthOf("Done") + widthOf("Eagle-required") - 1 })
    }

    // "Hiking" fits at its own width, as "Swimming" does in the first test.
    @Test
    fun anyLineThatWouldBreakAWord_keepsTheStatusFromFitting() {
        assertFalse(fits("Hiking", "Swimming") { widthOf("Done") + widthOf("Hiking") })
    }

    @Test
    fun aStatusAsWideAsTheRow_leavesNoRoom() {
        assertFalse(fits("A") { widthOf("Done") })
    }
}
