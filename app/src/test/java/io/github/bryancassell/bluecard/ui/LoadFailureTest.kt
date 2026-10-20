package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.testing.FakeWarningLog
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [catchLoadFailure]. The screen tests cover the load-failed message. */
class LoadFailureTest {
    private val log = FakeWarningLog()

    @Test
    fun ioException_isLoggedAndEmitsFailedValue() = runTest {
        val error = IOException("Can't read")
        val values = flow {
            emit("loaded")
            throw error
        }.catchLoadFailure("failed", log).toList()

        assertEquals(listOf("loaded", "failed"), values)
        assertEquals(
            listOf(FakeWarningLog.Warning("LoadFailure", "Couldn't load stored data", error)),
            log.warnings
        )
    }

    @Test
    fun otherException_isRethrownWithoutLogging() = runTest {
        val error = runCatching {
            flow<String> {
                throw IllegalStateException("A bug")
            }.catchLoadFailure("failed", log).toList()
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
        assertTrue(log.warnings.isEmpty())
    }
}
