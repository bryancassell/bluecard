package io.github.bryancassell.bluecard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import okio.IOException
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

class UIKitBackgroundTimeTest {
    private val tasks = FakeBackgroundTasks()
    private val time = UIKitBackgroundTime(tasks)

    @Test
    fun work_holdsATask_untilItFinishes() = runTest {
        val during = time.whileRunning { tasks.running.toList() }

        assertEquals(1, during.size)
        assertEquals(emptyList(), tasks.running)
        assertEquals(during, tasks.ended)
    }

    @Test
    fun workThatFails_endsItsTask() = runTest {
        val failure = runCatching { time.whileRunning { throw IOException("Disk full") } }

        assertIs<IOException>(failure.exceptionOrNull())
        assertEquals(emptyList(), tasks.running)
        assertEquals(1, tasks.ended.size)
    }

    // UIKit ends an app that doesn't end a task when its time runs out.
    @Test
    fun timeRunningOut_endsTheTask_once() = runTest {
        val result = time.whileRunning {
            tasks.runOut()
            // The work goes on, as it does until iOS suspends the app.
            assertEquals(emptyList(), tasks.running)
            "Saved"
        }

        assertEquals("Saved", result)
        assertEquals(1, tasks.ended.size)
    }

    // As when the app is out of background time.
    @Test
    fun taskUIKitRefuses_isntEnded_andTheWorkStillRuns() = runTest {
        tasks.refuse = true

        val result = time.whileRunning { "Saved" }

        assertEquals("Saved", result)
        assertEquals(emptyList(), tasks.ended)
    }

    @Test
    fun eachPieceOfWork_holdsItsOwnTask() = runTest {
        time.whileRunning {
            time.whileRunning { assertEquals(2, tasks.running.size) }
            assertEquals(1, tasks.running.size)
        }

        assertEquals(emptyList(), tasks.running)
        assertEquals(2, tasks.ended.size)
    }

    /** Begins tasks as UIKit does, and fails a test that ends one that isn't running. */
    private class FakeBackgroundTasks : BackgroundTasks {
        /** Whether to refuse each task, as UIKit does when the app is out of time. */
        var refuse = false

        /** The tasks begun and not yet ended, oldest first. */
        val running = mutableListOf<UIBackgroundTaskIdentifier>()

        /** The tasks ended, in the order they were. */
        val ended = mutableListOf<UIBackgroundTaskIdentifier>()

        private val expirationHandlers = mutableMapOf<UIBackgroundTaskIdentifier, () -> Unit>()
        private var next = UIBackgroundTaskInvalid + 1u

        override fun begin(name: String, expired: () -> Unit): UIBackgroundTaskIdentifier {
            if (refuse) return UIBackgroundTaskInvalid
            val task = next++
            running += task
            expirationHandlers[task] = expired
            return task
        }

        override fun end(task: UIBackgroundTaskIdentifier) {
            check(running.remove(task)) { "Ended task $task, which isn't running" }
            ended += task
        }

        /** Runs out the time of every task running, as UIKit does before it suspends the app. */
        fun runOut() {
            for (task in running.toList()) expirationHandlers.getValue(task)()
        }
    }
}
