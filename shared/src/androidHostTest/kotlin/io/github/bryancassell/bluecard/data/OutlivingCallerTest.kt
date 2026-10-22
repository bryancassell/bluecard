package io.github.bryancassell.bluecard.data

import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlivingCallerTest {
    // A thread of its own, so a test can tell work done there from work done in the caller's.
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // As the app's scope, which runs on the dispatcher that blocks switch to for disk work.
    private val scope = CoroutineScope(SupervisorJob() + io)

    @After
    fun closeDispatcher() {
        scope.cancel()
        io.close()
    }

    @Test
    fun block_switchingToTheScopesDispatcher_leavesTheCallersThread() = runTest {
        val caller = Thread.currentThread()

        val thread = scope.runOutlivingCaller { withContext(io) { Thread.currentThread() } }

        assertNotSame(caller, thread)
    }

    @Test
    fun blocks_startInTheOrderTheyreRun() = runTest {
        val started = mutableListOf<Int>()

        for (i in 1..3) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                scope.runOutlivingCaller { started += i }
            }
        }

        // Before runTest's dispatcher has run anything, so each started in its caller's thread.
        assertEquals(listOf(1, 2, 3), started)
    }

    @Test
    fun block_finishesEvenIfItsCallerIsCancelled() = runTest {
        val proceed = CompletableDeferred<Unit>()
        var finished = false

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.runOutlivingCaller {
                proceed.await()
                finished = true
            }
        }
        caller.cancel()
        proceed.complete(Unit)
        advanceUntilIdle()

        assertTrue(finished)
    }
}
