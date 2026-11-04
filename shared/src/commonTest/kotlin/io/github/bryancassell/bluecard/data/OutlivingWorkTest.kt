package io.github.bryancassell.bluecard.data

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okio.IOException

/** What [OutlivingWork] does on every platform. `OutlivingWorkThreadTest` checks its threads. */
class OutlivingWorkTest {
    // As the app's scope, which outlives the screens.
    private val scope = CoroutineScope(SupervisorJob())

    private val backgroundTime = CountingBackgroundTime()
    private val work = OutlivingWork(scope, backgroundTime)

    @AfterTest
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun blocks_startInTheOrderTheyreRun() = runTest {
        // Its scheduler never runs, so a block dispatched to it never starts.
        val heldScope = CoroutineScope(StandardTestDispatcher(TestCoroutineScheduler()))
        val heldWork = OutlivingWork(heldScope, backgroundTime)
        val started = mutableListOf<Int>()

        for (i in 1..3) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                heldWork.runOutlivingCaller { started += i }
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
                work.runOutlivingCaller {
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
        val holding = work.runOutlivingCaller { backgroundTime.holding }

        assertEquals(1, holding)
        assertEquals(0, backgroundTime.holding)
    }

    @Test
    fun blockThatFails_givesBackItsBackgroundTime() = runTest {
        val failure = runCatching { work.runOutlivingCaller { throw IOException("Disk full") } }

        assertIs<IOException>(failure.exceptionOrNull())
        assertEquals(1, backgroundTime.begins)
        assertEquals(0, backgroundTime.holding)
    }

    /**
     * Background time that counts what holds it. Work can begin and end on any thread, so both
     * counts are kept in one MutableStateFlow, which is thread-safe in common code too, and read
     * together.
     */
    private class CountingBackgroundTime : BackgroundTime {
        private data class Counts(val begun: Int = 0, val ended: Int = 0)

        private val counts = MutableStateFlow(Counts())

        /** How many pieces of work have held it. */
        val begins: Int get() = counts.value.begun

        /** How many pieces of work hold it now. */
        val holding: Int get() = counts.value.let { it.begun - it.ended }

        override suspend fun <T> whileRunning(block: suspend () -> T): T {
            counts.update { it.copy(begun = it.begun + 1) }
            try {
                return block()
            } finally {
                counts.update { it.copy(ended = it.ended + 1) }
            }
        }
    }
}
