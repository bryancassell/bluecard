package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.testing.FakeWarningLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okio.IOException

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

        assertTrue(error is IllegalStateException, "Expected the bug's exception, got $error")
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
