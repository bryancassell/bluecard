package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.DATE_PICKER_SCREEN
import io.github.bryancassell.bluecard.testing.assertIsWhollyDisplayed
import io.github.bryancassell.bluecard.testing.waitPastDateFieldFocusDelay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How the date picker fits the window. Robolectric's default screen is 320dp wide, as a Pixel
 * 10's is at the largest display size (#306): too narrow for the calendar. What the picker does
 * with the date picked is tested on the pages that open it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompletionDateTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private val today = LocalDate.of(2026, 5, 20)
    private val picked = mutableListOf<LocalDate>()

    private fun showPicker() {
        composeTestRule.setContent {
            CompletionDatePickerDialog(
                initial = LocalDate.of(2026, 5, 18),
                today = today,
                onConfirm = { picked += it },
                onDismiss = {}
            )
        }
    }

    // A day in the calendar, which reads each day as its full date.
    private fun calendarDay(date: String) =
        composeTestRule.onNode(hasText(date, substring = true) and hasClickAction())

    private fun typedDate() = composeTestRule.onNode(hasSetTextAction())

    private fun assertTypingTheDate() {
        composeTestRule.onNodeWithText("Cancel").assertIsWhollyDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsWhollyDisplayed()
        typedDate().assertIsWhollyDisplayed()
        calendarDay("May 18, 2026").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Switch to calendar input mode")
            .assertDoesNotExist()
    }

    // In touch mode, as on a phone without TalkBack or a keyboard. Out of touch mode, Android
    // gives a new window's first item focus, so a keyboard can type straight away.
    @Test
    fun onANarrowWindow_opensToTypingTheDate_withTheKeyboardDown() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        showPicker()
        composeTestRule.waitPastDateFieldFocusDelay()

        assertTypingTheDate()
        typedDate().assertIsNotFocused()
    }

    @Test
    fun onANarrowWindow_picksTheDateTyped() {
        showPicker()

        typedDate().performTextReplacement("05102026")
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf(LocalDate.of(2026, 5, 10)), picked)
    }

    @Test
    fun onANarrowWindow_aDateAfterTodayCantBeConfirmed() {
        showPicker()

        typedDate().performTextReplacement("05212026")
        composeTestRule.onNodeWithText("OK").assertIsNotEnabled()
        typedDate().performTextReplacement("05202026")
        composeTestRule.onNodeWithText("OK").assertIsEnabled()
    }

    // May 2026's Saturdays are in the calendar's last column, at the window's edge.
    @Config(qualifiers = DATE_PICKER_SCREEN)
    @Test
    fun onAWindowAsWideAsTheCalendar_showsAllOfIt() {
        showPicker()

        calendarDay("May 17, 2026").assertIsWhollyDisplayed()
        calendarDay("May 16, 2026").assertIsWhollyDisplayed()
        composeTestRule.onNodeWithContentDescription("Switch to text input mode")
            .assertIsWhollyDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsWhollyDisplayed()
    }

    @Config(qualifiers = DATE_PICKER_SCREEN)
    @Test
    fun onAWindowAsWideAsTheCalendar_switchingToTyping_focusesTheField() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        showPicker()

        composeTestRule.onNodeWithContentDescription("Switch to text input mode").performClick()
        composeTestRule.waitPastDateFieldFocusDelay()

        typedDate().assertIsFocused()
    }
}
