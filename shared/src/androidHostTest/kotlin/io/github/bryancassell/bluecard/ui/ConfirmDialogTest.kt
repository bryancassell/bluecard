package io.github.bryancassell.bluecard.ui

import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** What a ConfirmDialog does with taps as it opens. ConfirmDiscardOnBackTest checks the rest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfirmDialogTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private var showing by mutableStateOf(false)
    private var confirms = 0
    private var dismissals = 0
    private var doubleTapTimeout = 0L

    private fun launch() {
        composeTestRule.setContent {
            doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
            if (showing) {
                ConfirmDialog(
                    title = "Remove row?",
                    message = "It can't be undone.",
                    confirmLabel = "Remove",
                    onConfirm = { confirms++ },
                    onDismiss = { dismissals++ }
                )
            }
        }
    }

    /** Opens the dialog and holds time a few frames later, where a double tap's second tap lands. */
    private fun openAndHoldTime() {
        composeTestRule.mainClock.autoAdvance = false
        showing = true
        repeat(3) {
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithText("Remove row?").assertIsDisplayed()
    }

    private fun pressBackOnTheDialog() = composeTestRule.runOnIdle {
        // Espresso's pressBack doesn't reach the dialog's window under Robolectric.
        (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun tapsAndBack_asItOpens_doNothing() {
        launch()
        openAndHoldTime()

        composeTestRule.onNodeWithText("Remove").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        pressBackOnTheDialog()

        assertEquals(0, confirms)
        assertEquals(0, dismissals)
    }

    @Test
    fun taps_afterTheDoubleTapTimeout_work() {
        launch()
        openAndHoldTime()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeout)

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Remove").performClick()

        assertEquals(1, dismissals)
        assertEquals(1, confirms)
    }
}
