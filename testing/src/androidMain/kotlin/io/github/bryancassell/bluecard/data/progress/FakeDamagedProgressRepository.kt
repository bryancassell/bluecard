package io.github.bryancassell.bluecard.data.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory [DamagedProgressRepository] for other features' tests.
 * DamagedProgressRepositoryContract checks that it behaves like the file implementation.
 */
class FakeDamagedProgressRepository(noticePending: Boolean = false) : DamagedProgressRepository {
    private val noticePending = MutableStateFlow(noticePending)

    override fun observeNoticePending(): Flow<Boolean> = noticePending

    override suspend fun dismissNotice() {
        noticePending.value = false
    }

    /** Sets damaged progress aside, as the file implementation does for SQLite. */
    fun setAside() {
        noticePending.value = true
    }
}
