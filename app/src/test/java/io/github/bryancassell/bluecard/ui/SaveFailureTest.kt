package io.github.bryancassell.bluecard.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/**
 * [launchSave], with Robolectric so its log can be read back. The screen tests cover
 * [SaveFailedSnackbarHost].
 */
@RunWith(AndroidJUnit4::class)
class SaveFailureTest {
    private val failed = MutableStateFlow(false)

    @Test
    fun save_runsWithoutReportingAFailure() = runTest {
        var saved = false

        launchSave(failed) { saved = true }.join()

        assertTrue(saved)
        assertFalse(failed.value)
        assertTrue(ShadowLog.getLogsForTag("SaveFailure").isEmpty())
    }

    @Test
    fun ioException_isLoggedAndReported() = runTest {
        launchSave(failed) { throw IOException("Can't write") }.join()

        assertTrue(failed.value)
        val log = ShadowLog.getLogsForTag("SaveFailure").single()
        assertEquals(Log.WARN, log.type)
        assertTrue(log.throwable is IOException)
        assertEquals("Can't write", log.throwable.message)
    }

    @Test
    fun otherException_isRethrownWithoutLoggingOrReporting() = runTest {
        val error = runCatching {
            coroutineScope { launchSave(failed) { throw IllegalStateException("A bug") } }
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
        assertFalse(failed.value)
        assertTrue(ShadowLog.getLogsForTag("SaveFailure").isEmpty())
    }
}
