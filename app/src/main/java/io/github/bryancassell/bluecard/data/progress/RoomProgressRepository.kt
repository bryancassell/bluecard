package io.github.bryancassell.bluecard.data.progress

import androidx.room.withTransaction
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * [ProgressRepository] backed by Room.
 *
 * Functions are written as expression bodies (`= dao...`): when a call really suspends,
 * JaCoCo never sees a separate closing brace run, and would count it as untested.
 */
class RoomProgressRepository @Inject constructor(private val database: BlueCardDatabase) :
    ProgressRepository {
    private val dao = database.progressDao()

    override fun observeAllProgress(): Flow<List<BadgeProgressDetails>> = dao.observeAll()

    override fun observeProgress(badgeId: String): Flow<BadgeProgressDetails?> =
        dao.observe(badgeId)

    override suspend fun startBadge(
        badgeId: String,
        requirementsVersion: LocalDate,
        startedDate: LocalDate
    ) = dao.insertBadge(BadgeProgress(badgeId, requirementsVersion, startedDate))

    override suspend fun setCounselor(badgeId: String, counselor: Counselor?) = ifStarted(badgeId) {
        val stored = counselor?.normalized()
        dao.updateCounselor(badgeId, stored?.name, stored?.phone, stored?.email)
    }

    override suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate?) =
        ifStarted(badgeId) { dao.updateCompletedOnPriorDate(badgeId, date) }

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?
    ) = ifStarted(badgeId) {
        dao.updateRequirement(badgeId, number) {
            it.copy(completed = true, completedDate = completedDate)
        }
    }

    override suspend fun markRequirementNotCompleted(badgeId: String, number: String) =
        ifStarted(badgeId) {
            dao.updateRequirement(badgeId, number) {
                it.copy(completed = false, completedDate = null)
            }
        }

    override suspend fun setRequirementComment(badgeId: String, number: String, comment: String?) =
        ifStarted(badgeId) {
            dao.updateRequirement(badgeId, number) { it.copy(comment = comment?.ifBlank { null }) }
        }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        values: Map<String, String>
    ): Long = ifStarted(badgeId) {
        dao.insertTrackerEntry(
            TrackerEntry(badgeId = badgeId, requirementNumber = number, values = values)
        )
    }

    override suspend fun updateTrackerEntry(id: Long, values: Map<String, String>) =
        dao.updateTrackerEntry(id, values)

    override suspend fun deleteTrackerEntry(id: Long) = dao.deleteTrackerEntry(id)

    override suspend fun clearRequirement(badgeId: String, number: String) =
        dao.deleteRequirement(badgeId, number)

    override suspend fun clearBadge(badgeId: String) = dao.deleteBadge(badgeId)

    override suspend fun clearAll() = dao.deleteAll()

    /** Runs [action] if the badge is started, checking and writing in one transaction. */
    private suspend fun <T> ifStarted(badgeId: String, action: suspend () -> T): T =
        database.withTransaction {
            if (dao.isStarted(badgeId)) action() else throw notStartedError(badgeId)
        }
}
