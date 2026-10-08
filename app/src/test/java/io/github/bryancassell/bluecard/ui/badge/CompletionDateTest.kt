package io.github.bryancassell.bluecard.ui.badge

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.DATE_PICKER_SCREEN
import io.github.bryancassell.bluecard.testing.NARROW_SCREEN
import io.github.bryancassell.bluecard.testing.assertIsWhollyDisplayed
import io.github.bryancassell.bluecard.testing.pickerDateField
import io.github.bryancassell.bluecard.testing.pickerDay
import io.github.bryancassell.bluecard.testing.waitPastDateFieldFocusDelay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * How the date picker fits the window (#306). What the picker does with the date picked is
 * tested on the pages that open it.
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
    private var dismissals = 0

    private fun showPicker() {
        composeTestRule.setContent {
            CompletionDatePickerDialog(
                initial = LocalDate.of(2026, 5, 18),
                today = today,
                onConfirm = { picked += it },
                onDismiss = { dismissals++ }
            )
        }
    }

    private fun assertTypingTheDate() {
        composeTestRule.onNodeWithText("Cancel").assertIsWhollyDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsWhollyDisplayed()
        composeTestRule.pickerDateField().assertIsWhollyDisplayed()
        composeTestRule.pickerDay("May 18, 2026").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Switch to calendar input mode")
            .assertDoesNotExist()
    }

    // In touch mode, as on a phone without TalkBack or a keyboard. Out of touch mode, Android
    // gives a new window's first item focus, so a keyboard can type straight away.
    @Config(qualifiers = NARROW_SCREEN)
    @Test
    fun onANarrowWindow_opensToTypingTheDate_withTheKeyboardDown() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        showPicker()
        composeTestRule.waitPastDateFieldFocusDelay()

        assertTypingTheDate()
        composeTestRule.pickerDateField().assertIsNotFocused()
    }

    @Config(qualifiers = NARROW_SCREEN)
    @Test
    fun onANarrowWindow_picksTheDateTyped() {
        showPicker()

        composeTestRule.pickerDateField().performTextReplacement("05102026")
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf(LocalDate.of(2026, 5, 10)), picked)
    }

    @Config(qualifiers = NARROW_SCREEN)
    @Test
    fun onANarrowWindow_aDateAfterTodayCantBeConfirmed() {
        showPicker()

        composeTestRule.pickerDateField().performTextReplacement("05212026")
        composeTestRule.onNodeWithText("OK").assertIsNotEnabled()
        composeTestRule.pickerDateField().performTextReplacement("05202026")
        composeTestRule.onNodeWithText("OK").assertIsEnabled()
    }

    // The margin beside the dialog is outside it, as the space above and below is.
    @Config(qualifiers = NARROW_SCREEN)
    @Test
    fun onANarrowWindow_aTapBesideTheDialog_closesIt() {
        showPicker()

        composeTestRule.runOnIdle {
            val window = ShadowDialog.getLatestDialog().window!!.decorView
            val x = 8 * window.resources.displayMetrics.density
            val y = window.height / 2f
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                window.dispatchTouchEvent(MotionEvent.obtain(time, time, action, x, y, 0))
            }
        }

        assertEquals(1, dismissals)
    }

    // May 2026's Saturdays are in the calendar's last column, at the window's edge.
    @Config(qualifiers = DATE_PICKER_SCREEN)
    @Test
    fun onAWindowAsWideAsTheCalendar_showsAllOfIt() {
        showPicker()

        composeTestRule.pickerDay("May 17, 2026").assertIsWhollyDisplayed()
        composeTestRule.pickerDay("May 16, 2026").assertIsWhollyDisplayed()
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

        composeTestRule.pickerDateField().assertIsFocused()
    }

    // Larger text makes the picker taller. On a tall phone it scrolls inside a dialog no taller
    // than Material's, as #282's short windows do.
    @Config(qualifiers = "w411dp-h891dp", fontScale = 2f)
    @Test
    fun atTheLargestTextSize_onATallWindow_isNoTallerThanMaterialsDialog() {
        showPicker()

        val dialog = composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .fetchSemanticsNode()
        val height = with(composeTestRule.density) { dialog.size.height.toDp() }
        assertTrue("The dialog is $height tall", height <= 568.dp)
    }

    // Only on a window narrower than any phone's, at the largest text size, are they too wide
    // for one row. They keep the order they're read and focused in.
    @Config(qualifiers = "w200dp-h470dp", fontScale = 2f)
    @Test
    fun ifTheButtonsDontFitSideBySide_okGoesBelowCancel() {
        showPicker()

        val cancel = composeTestRule.onNodeWithText("Cancel").assertIsWhollyDisplayed()
            .getBoundsInRoot()
        val ok = composeTestRule.onNodeWithText("OK").assertIsWhollyDisplayed().getBoundsInRoot()
        assertTrue(ok.top >= cancel.bottom)
        assertEquals(cancel.right, ok.right)
    }
}
