package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.testing.FakeWarningLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.IOException

/** [catchLoadFailure]. The screen tests cover the load-failed message. */
class LoadFailureTest {
    private val warningLog = FakeWarningLog()

    @Test
    fun ioException_isLoggedAndEmitsFailedValue() = runTest {
        val error = IOException("Can't read")
        val values = flow {
            emit("loaded")
            throw error
        }.catchLoadFailure("failed", warningLog).toList()

        assertEquals(listOf("loaded", "failed"), values)
        assertEquals(
            listOf(FakeWarningLog.Warning("LoadFailure", "Couldn't load stored data", error)),
            warningLog.warnings
        )
    }

    @Test
    fun otherException_isRethrownWithoutLogging() = runTest {
        val error = runCatching {
            flow<String> {
                throw IllegalStateException("A bug")
            }.catchLoadFailure("failed", warningLog).toList()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "Expected the bug's exception, got $error")
        assertEquals("A bug", error?.message)
        assertTrue(warningLog.warnings.isEmpty())
    }
}
