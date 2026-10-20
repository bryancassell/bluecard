package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.testing.FakeWarningLog
import java.io.IOException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TaskRunner]. [TaskFailureTest] covers the snackbar that shows its failures. */
class TaskRunnerTest {
    private val warningLog = FakeWarningLog()

    @Test
    fun task_runsWithoutReportingAFailure() = runTest {
        val tasks = TaskRunner(this, warningLog)
        var ran = false

        tasks.launch { ran = true }.join()

        assertTrue(ran)
        assertNull(tasks.failure.value)
        assertTrue(warningLog.warnings.isEmpty())
    }

    @Test
    fun ioException_isLoggedAndReported() = runTest {
        val tasks = TaskRunner(this, warningLog)
        val error = IOException("Can't write")

        tasks.launch { throw error }.join()

        assertNotNull(tasks.failure.value)
        assertEquals(
            listOf(FakeWarningLog.Warning("TaskFailure", "Task failed", error)),
            warningLog.warnings
        )
    }

    @Test
    fun otherException_isRethrownWithoutLoggingOrReporting() = runTest {
        var tasks: TaskRunner? = null
        val error = runCatching {
            coroutineScope {
                tasks = TaskRunner(this, warningLog).apply {
                    launch { throw IllegalStateException("A bug") }
                }
            }
        }.exceptionOrNull()

        assertTrue("Expected the bug's exception, got $error", error is IllegalStateException)
        assertEquals("A bug", error?.message)
        assertNull(tasks?.failure?.value)
        assertTrue(warningLog.warnings.isEmpty())
    }

    @Test
    fun shown_clearsTheFailure() = runTest {
        val tasks = TaskRunner(this, warningLog)
        tasks.launch { throw IOException("Can't write") }.join()

        tasks.onShown(tasks.failure.value!!)

        assertNull(tasks.failure.value)
    }

    @Test
    fun failureAfterTheOneShown_staysToBeShown() = runTest {
        val tasks = TaskRunner(this, warningLog)
        tasks.launch { throw IOException("Can't write") }.join()
        val first = tasks.failure.value!!
        tasks.launch { throw IOException("Can't write again") }.join()
        val second = tasks.failure.value!!

        tasks.onShown(first)

        assertNotSame(first, second)
        assertSame(second, tasks.failure.value)
    }
}
