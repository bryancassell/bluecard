package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertEquals

/**
 * A screen as wide as the date picker's calendar, a small phone's, for every test that picks a
 * day from it. On a narrower screen, the scout types the date instead (#306).
 */
const val DATE_PICKER_SCREEN = "w360dp-h560dp"

/**
 * A screen too narrow for the date picker's calendar, 320dp wide like a Pixel 10's at its
 * largest display size. It's also Robolectric's default screen.
 */
const val NARROW_SCREEN = "w320dp-h470dp"

/** A day in the date picker's calendar, which reads each day as its full date. */
fun ComposeTestRule.pickerDay(date: String) =
    onNode(hasText(date, substring = true) and hasClickAction())

/** The date picker's field for typing the date. */
fun ComposeTestRule.pickerDateField() = onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))

/**
 * Asserts none of the node is clipped, by its parents or the edges of its root view, as a date
 * picker's day must not be to keep its 48dp touch target. assertIsDisplayed passes once any of
 * it shows. Bounds in the root aren't clipped at the root's own edges, so a dialog's content
 * wider than its window passed them.
 */
fun SemanticsNodeInteraction.assertIsWhollyDisplayed() = apply {
    val node = fetchSemanticsNode()
    assertEquals(node.size.toSize(), node.boundsInWindow.size)
}

/**
 * Moves the clock past the 300ms Material waits after the date picker's typed field appears
 * before it focuses it (MotionTokens.DurationMedium2), which opens the keyboard. Compose's test
 * rules don't skip that wait, so a test that the field didn't take focus needs this first.
 */
fun ComposeTestRule.waitPastDateFieldFocusDelay() = mainClock.advanceTimeBy(1_000)
