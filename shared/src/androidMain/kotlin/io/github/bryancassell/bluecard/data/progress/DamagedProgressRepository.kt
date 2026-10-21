package io.github.bryancassell.bluecard.data.progress

import kotlinx.coroutines.flow.Flow

/**
 * Progress set aside because SQLite found the database damaged, and whether the scout has been
 * told (#70).
 */
interface DamagedProgressRepository {
    /** Whether progress was set aside since the scout last dismissed the notice saying so. */
    fun observeNoticePending(): Flow<Boolean>

    /** Records that the scout has seen the notice. */
    suspend fun dismissNotice()
}
