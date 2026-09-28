package io.github.bryancassell.bluecard.data.progress

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * [ProgressRepository] backed by Room.
 *
 * Functions are written as expression bodies (`= dao...`): when a call really suspends,
 * JaCoCo never sees a separate closing brace run, and would count it as untested.
 */
class RoomProgressRepository @Inject constructor(private val dao: ProgressDao) :
    ProgressRepository {
    override fun observeAllProgress(): Flow<List<BadgeProgressDetails>> = dao.observeAll()

    override fun observeProgress(badgeId: String): Flow<BadgeProgressDetails?> =
        dao.observe(badgeId)

    override suspend fun startBadge(
        badgeId: String,
        requirementsVersion: LocalDate,
        startedDate: LocalDate
    ) = dao.insertBadge(BadgeProgress(badgeId, requirementsVersion, startedDate))

    override suspend fun setCounselor(badgeId: String, counselor: Counselor?) =
        dao.updateCounselor(badgeId, counselor?.name, counselor?.phone, counselor?.email)

    override suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate?) =
        dao.updateCompletedOnPriorDate(badgeId, date)

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?
    ) = dao.updateRequirement(badgeId, number) {
        it.copy(completed = true, completedDate = completedDate)
    }

    override suspend fun markRequirementNotCompleted(badgeId: String, number: String) =
        dao.updateRequirement(badgeId, number) { it.copy(completed = false, completedDate = null) }

    override suspend fun setRequirementComment(badgeId: String, number: String, comment: String?) =
        dao.updateRequirement(badgeId, number) { it.copy(comment = comment?.ifBlank { null }) }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        values: Map<String, String>
    ): Long = dao.insertTrackerEntry(
        TrackerEntry(badgeId = badgeId, requirementNumber = number, values = values)
    )

    override suspend fun updateTrackerEntry(id: Long, values: Map<String, String>) =
        dao.updateTrackerEntry(id, values)

    override suspend fun deleteTrackerEntry(id: Long) = dao.deleteTrackerEntry(id)

    override suspend fun clearRequirement(badgeId: String, number: String) =
        dao.deleteRequirement(badgeId, number)

    override suspend fun clearBadge(badgeId: String) = dao.deleteBadge(badgeId)

    override suspend fun clearAll() = dao.deleteAll()
}
