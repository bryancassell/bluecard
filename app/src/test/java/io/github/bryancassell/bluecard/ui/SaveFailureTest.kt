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
 * [SaveRunner] and [SaveFailedSnackbarHost], with Robolectric so the log can be read back and
 * the snackbar shown.
 */
@RunWith(AndroidJUnit4::class)
class SaveFailureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val message = "Couldn't save. Try again."

    @Test
    fun save_runsWithoutReportingAFailure() = runTest {
        val saves = SaveRunner(this)
        var saved = false

        saves.launch { saved = true }.join()

        assertTrue(saved)
        assertNull(saves.failure.value)
        assertTrue(ShadowLog.getLogsForTag("SaveFailure").isEmpty())
    }

    @Test
    fun ioException_isLoggedAndReported() = runTest {
        val saves = SaveRunner(this)

        saves.launch { throw IOException("Can't write") }.join()

        assertNotNull(saves.failure.value)
        val log = ShadowLog.getLogsForTag("SaveFailure").single()
        assertEquals(Log.WARN, log.type)
        assertTrue(log.throwable is IOException)
        assertEquals("Can't write", log.throwable.message)
    }

    @Test
    fun otherException_isRethrownWithoutLoggingOrReporting() = runTest {
        var saves: SaveRunner? = null
        val error = runCatching {
            coroutineScope {
                saves = SaveRunner(this).apply { launch { throw IllegalStateException("A bug") } }
            }
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
        assertNull(saves?.failure?.value)
        assertTrue(ShadowLog.getLogsForTag("SaveFailure").isEmpty())
    }

    @Test
    fun shown_clearsTheFailure() = runTest {
        val saves = SaveRunner(this)
        saves.launch { throw IOException("Can't write") }.join()

        saves.onShown(saves.failure.value!!)

        assertNull(saves.failure.value)
    }

    @Test
    fun failureAfterTheOneShown_staysToBeShown() = runTest {
        val saves = SaveRunner(this)
        saves.launch { throw IOException("Can't write") }.join()
        val first = saves.failure.value!!
        saves.launch { throw IOException("Can't write again") }.join()
        val second = saves.failure.value!!

        saves.onShown(first)

        assertNotSame(first, second)
        assertSame(second, saves.failure.value)
    }

    @Test
    fun snackbar_showsFailure_thenReportsItShown() {
        val failure = SaveFailure()
        val shown = mutableListOf<SaveFailure>()
        composeTestRule.setContent { SaveFailedSnackbarHost(failure, onShown = { shown += it }) }

        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(emptyList<SaveFailure>(), shown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(message).assertDoesNotExist()
        assertEquals(listOf(failure), shown)
    }

    @Test
    fun snackbar_showsFailureThatReplacesAnother() {
        val first = SaveFailure()
        val second = SaveFailure()
        var failure by mutableStateOf<SaveFailure?>(first)
        val shown = mutableListOf<SaveFailure>()
        composeTestRule.setContent { SaveFailedSnackbarHost(failure, onShown = { shown += it }) }
        composeTestRule.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf(first), shown)

        // Another save failed in the frame the first was shown, so it never went back to null.
        failure = second

        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf(first, second), shown)
    }

    @Test
    fun snackbar_leftWhileShowing_isReportedShown() {
        val failure = SaveFailure()
        var onScreen by mutableStateOf(true)
        val shown = mutableListOf<SaveFailure>()
        composeTestRule.setContent {
            if (onScreen) SaveFailedSnackbarHost(failure, onShown = { shown += it })
        }
        composeTestRule.onNodeWithText(message).assertIsDisplayed()

        // As when the scout opens another screen before the message times out.
        onScreen = false
        composeTestRule.waitForIdle()

        assertEquals(listOf(failure), shown)
    }

    @Test
    fun snackbar_withNoFailure_showsNothing() {
        composeTestRule.setContent { SaveFailedSnackbarHost(null, onShown = {}) }

        composeTestRule.onNodeWithText(message).assertDoesNotExist()
    }
}
