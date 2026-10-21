package io.github.bryancassell.bluecard.data.progress

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior every [DamagedProgressRepository] must have. Runs against the file implementation
 * (FileDamagedProgressRepositoryTest) and the fake (FakeDamagedProgressRepositoryTest).
 */
abstract class DamagedProgressRepositoryContract {
    protected abstract val repository: DamagedProgressRepository

    /** Sets damaged progress aside, as happens when SQLite finds the database damaged. */
    protected abstract fun setAsideDamagedProgress()

    @Test
    fun observeNoticePending_whenNothingIsSetAside_isFalse() = runTest {
        assertFalse(repository.observeNoticePending().first())
    }

    @Test
    fun observeNoticePending_afterProgressIsSetAside_isTrue() = runTest {
        setAsideDamagedProgress()

        assertTrue(repository.observeNoticePending().first())
    }

    @Test
    fun observeNoticePending_whenProgressIsSetAsideWhileObserving_becomesTrue() = runTest {
        val values = mutableListOf<Boolean>()
        val observing = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNoticePending().take(2).toList(values)
        }

        setAsideDamagedProgress()
        observing.join()

        assertEquals(listOf(false, true), values)
    }

    @Test
    fun dismissNotice_makesItNotPending() = runTest {
        setAsideDamagedProgress()

        repository.dismissNotice()

        assertFalse(repository.observeNoticePending().first())
    }

    @Test
    fun setAside_afterNoticeIsDismissed_makesItPendingAgain() = runTest {
        setAsideDamagedProgress()
        repository.dismissNotice()

        setAsideDamagedProgress()

        assertTrue(repository.observeNoticePending().first())
    }
}
