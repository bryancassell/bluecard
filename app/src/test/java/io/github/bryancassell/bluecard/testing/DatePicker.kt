package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertEquals

/**
 * A screen as wide as the date picker's calendar, a small phone's, for every test that picks a
 * day from it. On Robolectric's default screen, 320dp wide, the scout types the date instead
 * (#306).
 */
const val DATE_PICKER_SCREEN = SMALL_PHONE

/**
 * Asserts none of the node is clipped, by its parents or the window's edge, as a date picker's
 * day must not be to keep its 48dp touch target. assertIsDisplayed passes once any of it shows.
 * Bounds in the root aren't enough: a dialog's content can be wider than its window.
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
