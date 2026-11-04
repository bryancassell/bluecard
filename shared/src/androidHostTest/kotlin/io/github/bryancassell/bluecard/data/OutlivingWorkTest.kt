package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.testing.FakeBackgroundTime
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlivingWorkTest {
    // A thread of its own, so a test can tell work done there from work done in the caller's.
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // As the app's scope, which runs on the dispatcher that blocks switch to for disk work.
    private val scope = CoroutineScope(SupervisorJob() + io)

    private val backgroundTime = FakeBackgroundTime()
    private val work = OutlivingWork(scope, backgroundTime)

    @After
    fun closeDispatcher() {
        scope.cancel()
        io.close()
    }

    @Test
    fun block_switchingToTheScopesDispatcher_leavesTheCallersThread() = runTest {
        val caller = Thread.currentThread()

        val thread = work.run { withContext(io) { Thread.currentThread() } }

        assertNotSame(caller, thread)
    }

    @Test
    fun block_switchingToADispatcherTheCallersContextNames_leavesTheCallersThread() = runTest {
        val caller = Thread.currentThread()
        var thread: Thread? = null

        // Runs in this thread until it suspends, with io in its context.
        launch(io, start = CoroutineStart.UNDISPATCHED) {
            thread = work.run { withContext(io) { Thread.currentThread() } }
        }.join()

        assertNotSame(caller, thread)
    }

    @Test
    fun blocks_startInTheOrderTheyreRun() = runTest {
        // Its scheduler never runs, so a block dispatched to it never starts.
        val heldScope = CoroutineScope(StandardTestDispatcher(TestCoroutineScheduler()))
        val heldWork = OutlivingWork(heldScope, backgroundTime)
        val started = mutableListOf<Int>()

        for (i in 1..3) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                heldWork.run { started += i }
            }
        }

        // Before runTest's dispatcher has run anything, so each started in its caller's thread.
        assertEquals(listOf(1, 2, 3), started)
    }

    @Test
    fun block_finishesEvenIfItsCallerIsCancelled_holdingBackgroundTimeUntilItDoes() =
        runTest(timeout = 10.seconds) {
            val proceed = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()

            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                work.run {
                    proceed.await()
                    finished.complete(Unit)
                }
            }
            caller.cancel()
            assertEquals(1, backgroundTime.holding)
            proceed.complete(Unit)

            finished.await()
            // Until the block has returned, as well as finished its work.
            scope.coroutineContext.job.children.toList().joinAll()
            assertEquals(0, backgroundTime.holding)
        }

    @Test
    fun block_holdsBackgroundTime_whileItRuns() = runTest {
        val holding = work.run { backgroundTime.holding }

        assertEquals(1, holding)
        assertEquals(0, backgroundTime.holding)
    }

    @Test
    fun blockThatFails_givesBackItsBackgroundTime() = runTest {
        val failure = runCatching { work.run { throw IOException("Disk full") } }

        assertTrue(failure.exceptionOrNull() is IOException)
        assertEquals(1, backgroundTime.begins)
        assertEquals(0, backgroundTime.holding)
    }
}
