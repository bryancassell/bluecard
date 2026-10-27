package io.github.bryancassell.bluecard.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val holder = thread {
            lock.withLock {
                held.countDown()
                release.await()
            }
        }
        held.await()

        val waiter = thread { lock.withLock { entered.countDown() } }

        try {
            assertFalse(entered.await(200, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        holder.join()
        waiter.join()
    }

    @Test
    fun withLock_inTheThreadThatHoldsIt_runsRightAway() {
        assertEquals("inner", lock.withLock { lock.withLock { "inner" } })
    }
}
