package io.github.bryancassell.bluecard.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_t
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time

// Android's LockTest, with Grand Central Dispatch's threads and semaphores. Each wait has a time
// limit, so a lock that never lets go fails a test rather than hanging the run.
class LockTest {
    private val lock = Lock()

    @Test
    fun withLock_returnsWhatItsActionReturns() {
        assertEquals("done", lock.withLock { "done" })
    }

    @Test
    fun withLock_whileAnotherThreadHoldsIt_waitsUntilItsReleased() {
        val held = dispatch_semaphore_create(0)
        val release = dispatch_semaphore_create(0)
        val entered = dispatch_semaphore_create(0)
        inBackground {
            lock.withLock {
                dispatch_semaphore_signal(held)
                release.await(milliseconds = 5_000)
            }
        }
        assertTrue(held.await(milliseconds = 5_000))

        inBackground { lock.withLock { dispatch_semaphore_signal(entered) } }

        try {
            assertFalse(entered.await(milliseconds = 200))
        } finally {
            dispatch_semaphore_signal(release)
        }
        assertTrue(entered.await(milliseconds = 5_000))
    }

    // On another thread, so a lock that can't be taken again blocks that thread, not the test's.
    @Test
    fun withLock_inTheThreadThatHoldsIt_runsRightAway() {
        var taken: String? = null
        val done = dispatch_semaphore_create(0)

        inBackground {
            taken = lock.withLock { lock.withLock { "again" } }
            dispatch_semaphore_signal(done)
        }

        assertTrue(done.await(milliseconds = 5_000))
        assertEquals("again", taken)
    }

    private fun inBackground(action: () -> Unit) = dispatch_async(
        dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u),
        action
    )

    /** Waits until the semaphore is signaled, for up to [milliseconds]. Returns whether it was. */
    private fun dispatch_semaphore_t.await(milliseconds: Long): Boolean = dispatch_semaphore_wait(
        this,
        dispatch_time(DISPATCH_TIME_NOW, milliseconds * NSEC_PER_MSEC.toLong())
    ) == 0L
}
