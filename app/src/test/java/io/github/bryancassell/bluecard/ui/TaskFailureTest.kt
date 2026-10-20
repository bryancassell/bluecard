package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.isPoliteLiveRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [TaskFailureSnackbarHost], with Robolectric so the snackbar can be shown. */
@RunWith(AndroidJUnit4::class)
class TaskFailureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val message = "Couldn't save. Try again."

    @Test
    fun snackbar_showsFailure_thenReportsItShown() {
        val failure = TaskFailure()
        val shown = mutableListOf<TaskFailure>()
        composeTestRule.setContent {
            TaskFailureSnackbarHost(failure, message, onShown = { shown += it })
        }

        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), shown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(message).assertDoesNotExist()
        assertEquals(listOf(failure), shown)
    }

    // TalkBack reads each snackbar's message from the live region Material gives it, including
    // one that replaces another still showing, when it skips the pane title (#234).
    @Test
    fun snackbar_thatReplacesAnotherStillShowing_isANewPoliteLiveRegion() {
        var failure by mutableStateOf(TaskFailure())
        composeTestRule.setContent { TaskFailureSnackbarHost(failure, message, onShown = {}) }
        val messageInLiveRegion = hasText(message) and hasAnyAncestor(isPoliteLiveRegion)
        val first = composeTestRule.onNode(messageInLiveRegion).assertIsDisplayed()
            .fetchSemanticsNode().id

        failure = TaskFailure()

        val second = composeTestRule.onNode(messageInLiveRegion).assertIsDisplayed()
            .fetchSemanticsNode().id
        // A new snackbar, not the first one still showing.
        assertNotEquals(first, second)
    }

    @Test
    fun snackbar_showsFailureThatReplacesAnother() {
        val first = TaskFailure()
        val second = TaskFailure()
        var failure by mutableStateOf<TaskFailure?>(first)
        val shown = mutableListOf<TaskFailure>()
        composeTestRule.setContent {
            TaskFailureSnackbarHost(failure, message, onShown = { shown += it })
        }
        composeTestRule.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf(first), shown)

        // Another task failed in the frame the first was shown, so it never went back to null.
        failure = second

        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf(first, second), shown)
    }

    @Test
    fun snackbar_leftWhileShowing_isReportedShown() {
        val failure = TaskFailure()
        var onScreen by mutableStateOf(true)
        val shown = mutableListOf<TaskFailure>()
        composeTestRule.setContent {
            if (onScreen) TaskFailureSnackbarHost(failure, message, onShown = { shown += it })
        }
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        // As when the scout opens another screen before the message times out.
        onScreen = false
        composeTestRule.waitForIdle()

        assertEquals(listOf(failure), shown)
    }

    @Test
    fun snackbar_withNoFailure_showsNothing() {
        composeTestRule.setContent { TaskFailureSnackbarHost(null, message, onShown = {}) }

        composeTestRule.onNodeWithText(message).assertDoesNotExist()
    }
}
