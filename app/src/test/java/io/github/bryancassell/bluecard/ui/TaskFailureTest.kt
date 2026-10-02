package io.github.bryancassell.bluecard.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/**
 * [TaskRunner] and [TaskFailureSnackbarHost], with Robolectric so the log can be read back and
 * the snackbar shown.
 */
@RunWith(AndroidJUnit4::class)
class TaskFailureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val message = "Couldn't save. Try again."

    @Test
    fun task_runsWithoutReportingAFailure() = runTest {
        val tasks = TaskRunner(this)
        var ran = false

        tasks.launch { ran = true }.join()

        assertTrue(ran)
        assertNull(tasks.failure.value)
        assertTrue(ShadowLog.getLogsForTag("TaskFailure").isEmpty())
    }

    @Test
    fun ioException_isLoggedAndReported() = runTest {
        val tasks = TaskRunner(this)

        tasks.launch { throw IOException("Can't write") }.join()

        assertNotNull(tasks.failure.value)
        val log = ShadowLog.getLogsForTag("TaskFailure").single()
        assertEquals(Log.WARN, log.type)
        assertTrue(log.throwable is IOException)
        assertEquals("Can't write", log.throwable.message)
    }

    @Test
    fun otherException_isRethrownWithoutLoggingOrReporting() = runTest {
        var tasks: TaskRunner? = null
        val error = runCatching {
            coroutineScope {
                tasks = TaskRunner(this).apply { launch { throw IllegalStateException("A bug") } }
            }
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
        assertNull(tasks?.failure?.value)
        assertTrue(ShadowLog.getLogsForTag("TaskFailure").isEmpty())
    }

    @Test
    fun shown_clearsTheFailure() = runTest {
        val tasks = TaskRunner(this)
        tasks.launch { throw IOException("Can't write") }.join()

        tasks.onShown(tasks.failure.value!!)

        assertNull(tasks.failure.value)
    }

    @Test
    fun failureAfterTheOneShown_staysToBeShown() = runTest {
        val tasks = TaskRunner(this)
        tasks.launch { throw IOException("Can't write") }.join()
        val first = tasks.failure.value!!
        tasks.launch { throw IOException("Can't write again") }.join()
        val second = tasks.failure.value!!

        tasks.onShown(first)

        assertNotSame(first, second)
        assertSame(second, tasks.failure.value)
    }

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
