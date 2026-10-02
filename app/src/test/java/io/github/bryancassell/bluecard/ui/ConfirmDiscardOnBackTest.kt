package io.github.bryancassell.bluecard.ui

import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.BackPresses
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class ConfirmDiscardOnBackTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val back = BackPresses()
    private var changed by mutableStateOf(false)
    private var discards = 0

    private fun show(message: String? = null) {
        composeTestRule.setContent {
            back.Content {
                if (message == null) {
                    ConfirmDiscardOnBack(changed, onDiscard = { discards++ })
                } else {
                    ConfirmDiscardOnBack(changed, onDiscard = { discards++ }, message = message)
                }
            }
        }
    }

    private fun pressBack() = back.press(composeTestRule)

    private fun dialogTitle() = composeTestRule.onNodeWithText("Discard changes?")

    @Test
    fun back_withoutChanges_closesThePage() {
        show()

        pressBack()

        assertEquals(1, back.closes)
        dialogTitle().assertDoesNotExist()
    }

    @Test
    fun back_withChanges_asksFirst() {
        changed = true
        show()

        pressBack()

        assertEquals(0, back.closes)
        dialogTitle().assertIsDisplayed()
        composeTestRule.onNodeWithText("Your changes haven't been saved.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        assertEquals(0, discards)
    }

    @Test
    fun discard_discardsTheChanges() {
        changed = true
        show()

        pressBack()
        composeTestRule.onNodeWithText("Discard").performClick()

        assertEquals(1, discards)
        assertEquals(0, back.closes)
        dialogTitle().assertDoesNotExist()
    }

    @Test
    fun cancel_keepsTheChanges() {
        changed = true
        show()

        pressBack()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, discards)
        assertEquals(0, back.closes)
        dialogTitle().assertDoesNotExist()
    }

    @Test
    fun backOnTheDialog_keepsTheChanges() {
        changed = true
        show()

        pressBack()
        // Espresso's pressBack doesn't reach the dialog's window under Robolectric, so Back is
        // sent to the dialog itself.
        composeTestRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
                .onBackPressed()
        }

        assertEquals(0, discards)
        assertEquals(0, back.closes)
        dialogTitle().assertDoesNotExist()
    }

    @Test
    fun message_isThePagesOwn() {
        changed = true
        show(message = "Your changes to the notes haven't been saved.")

        pressBack()

        composeTestRule.onNodeWithText("Your changes to the notes haven't been saved.")
            .assertIsDisplayed()
    }

    @Test
    fun changesSaved_whileAsking_closeTheDialogForGood() {
        changed = true
        show()
        pressBack()

        // As when a save finishes while the dialog is open.
        changed = false
        composeTestRule.waitForIdle()
        dialogTitle().assertDoesNotExist()

        // New changes don't bring it back until Back.
        changed = true
        composeTestRule.waitForIdle()
        dialogTitle().assertDoesNotExist()
        pressBack()
        dialogTitle().assertIsDisplayed()
    }

    @Test
    fun back_onceChangesAreSaved_closesThePage() {
        changed = true
        show()

        changed = false
        composeTestRule.waitForIdle()
        pressBack()

        assertEquals(1, back.closes)
        dialogTitle().assertDoesNotExist()
    }
}
