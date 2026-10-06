package io.github.bryancassell.bluecard.ui

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/**
 * [catchLoadFailure], with Robolectric so its log can be read back. The screen tests cover the
 * load-failed message.
 */
@RunWith(AndroidJUnit4::class)
class LoadFailureTest {
    @Test
    fun ioException_isLoggedAndEmitsFailedValue() = runTest {
        val values = flow {
            emit("loaded")
            throw IOException("Can't read")
        }.catchLoadFailure("failed").toList()

        assertEquals(listOf("loaded", "failed"), values)
        val log = ShadowLog.getLogsForTag("LoadFailure").single()
        assertEquals(Log.WARN, log.type)
        assertTrue(log.throwable is IOException)
        assertEquals("Can't read", log.throwable.message)
    }

    @Test
    fun otherException_isRethrownWithoutLogging() = runTest {
        val error = runCatching {
            flow<String> {
                throw IllegalStateException("A bug")
            }.catchLoadFailure("failed").toList()
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
        assertTrue(ShadowLog.getLogsForTag("LoadFailure").isEmpty())
    }
}
