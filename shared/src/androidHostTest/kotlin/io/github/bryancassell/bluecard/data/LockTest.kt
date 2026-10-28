package io.github.bryancassell.bluecard.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Each wait has a time limit, and the threads are daemons, so a lock that never lets go fails a
// test rather than hanging the run.
class LockTest {
    private val lock = Lock()

    @Test
    fun withLock_returnsWhatItsActionReturns() {
        assertEquals("done", lock.withLock { "done" })
    }

    @Test
    fun withLock_whileAnotherThreadHoldsIt_waitsUntilItsReleased() {
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        thread(isDaemon = true) {
            lock.withLock {
                held.countDown()
                release.await()
            }
        }
        assertTrue(held.await(5, SECONDS))

        thread(isDaemon = true) { lock.withLock { entered.countDown() } }

        try {
            assertFalse(entered.await(200, MILLISECONDS))
        } finally {
            release.countDown()
        }
        assertTrue(entered.await(5, SECONDS))
    }

    @Test
    fun withLock_inTheThreadThatHoldsIt_runsRightAway() {
        var taken: String? = null

        val taker = thread(isDaemon = true) { taken = lock.withLock { lock.withLock { "again" } } }

        taker.join(5_000)
        assertEquals("again", taken)
    }
}
