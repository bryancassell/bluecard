package io.github.bryancassell.bluecard.data

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Test

/** Which threads [OutlivingWork]'s blocks run in, with JVM threads. */
class OutlivingWorkThreadTest {
    // A thread of its own, so a test can tell work done there from work done in the caller's.
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // As the app's scope, which runs on the dispatcher that blocks switch to for disk work.
    private val scope = CoroutineScope(SupervisorJob() + io)

    private val work = OutlivingWork(scope, NoBackgroundTime)

    @After
    fun closeDispatcher() {
        scope.cancel()
        io.close()
    }

    @Test
    fun block_switchingToTheScopesDispatcher_leavesTheCallersThread() = runTest {
        val caller = Thread.currentThread()

        val thread = work.runOutlivingCaller { withContext(io) { Thread.currentThread() } }

        assertNotSame(caller, thread)
    }

    @Test
    fun block_switchingToADispatcherTheCallersContextNames_leavesTheCallersThread() = runTest {
        val caller = Thread.currentThread()
        var thread: Thread? = null

        // Runs in this thread until it suspends, with io in its context.
        launch(io, start = CoroutineStart.UNDISPATCHED) {
            thread = work.runOutlivingCaller { withContext(io) { Thread.currentThread() } }
        }.join()

        assertNotSame(caller, thread)
    }
}
