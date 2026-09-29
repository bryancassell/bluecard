package io.github.bryancassell.bluecard.ui

import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [catchLoadFailure]. The screen tests cover [LoadFailedMessage]. */
class LoadFailureTest {
    @Test
    fun ioException_emitsFailedValue() = runTest {
        val values = flow {
            emit("loaded")
            throw IOException("Can't read")
        }.catchLoadFailure("failed").toList()

        assertEquals(listOf("loaded", "failed"), values)
    }

    @Test
    fun otherException_isRethrown() = runTest {
        val error = runCatching {
            flow<String> {
                throw IllegalStateException("A bug")
            }.catchLoadFailure("failed").toList()
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
    }
}
