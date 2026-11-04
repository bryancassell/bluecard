package io.github.bryancassell.bluecard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.IOException
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.usleep

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

    // As when work begins off the main thread, and the handler runs on the main thread before
    // begin returns. UIKit ends the app if a task is still running once its handler returns.
    @Test
    fun timeRunningOutOnAnotherThreadWhileBeginning_isEndedByTheHandler() = runTest {
        tasks.runOutOnAnotherThreadWhileBeginning = true

        time.whileRunning { tasks.awaitHandler() }

        assertEquals(true, tasks.endedByHandler)
        assertEquals(1, tasks.ended.size)
    }

    // As when the handler runs inside begin, on the thread that's beginning.
    @Test
    fun timeRunningOutInsideBegin_endsTheTask_once() = runTest {
        tasks.runOutWhileBeginning = true

        val during = time.whileRunning { tasks.running.toList() }

        assertEquals(emptyList(), during)
        assertEquals(1, tasks.ended.size)
    }

    // Outliving work starts in the order it's run only if this calls it at once.
    @Test
    fun work_startsBeforeWhileRunningSuspends() = runTest {
        val proceed = CompletableDeferred<Unit>()
        var started = false

        launch(start = CoroutineStart.UNDISPATCHED) {
            time.whileRunning {
                started = true
                proceed.await()
            }
        }

        assertTrue(started)
        proceed.complete(Unit)
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
    @OptIn(ExperimentalForeignApi::class)
    private class FakeBackgroundTasks : BackgroundTasks {
        /** Whether to refuse each task, as UIKit does when the app is out of time. */
        var refuse = false

        /** Whether each task's time runs out inside [begin], on the thread that's beginning. */
        var runOutWhileBeginning = false

        /**
         * Whether each task's time runs out on another thread while [begin] is still running, as
         * UIKit's main thread can while work begins on another.
         */
        var runOutOnAnotherThreadWhileBeginning = false

        /** Whether the task was ended by the time its expiration handler returned. */
        var endedByHandler: Boolean? = null
            private set

        private val handlerDone = dispatch_semaphore_create(0)

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
            if (runOutWhileBeginning) expired()
            if (runOutOnAnotherThreadWhileBeginning) {
                dispatch_async(
                    dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
                ) {
                    expired()
                    endedByHandler = task !in running
                    dispatch_semaphore_signal(handlerDone)
                }
                // Time for the handler to run before begin returns.
                usleep(100_000u)
            }
            return task
        }

        /** Waits for the handler that [runOutOnAnotherThreadWhileBeginning] runs to return. */
        fun awaitHandler() {
            dispatch_semaphore_wait(handlerDone, DISPATCH_TIME_FOREVER)
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
