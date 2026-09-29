package io.github.bryancassell.bluecard.data.progress

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * An in-memory [ProgressRepository] for other features' tests. ProgressRepositoryContract
 * checks that it behaves like the Room implementation.
 */
class FakeProgressRepository : ProgressRepository {
    private val badges = MutableStateFlow<Map<String, BadgeProgressDetails>>(emptyMap())
    private var nextTrackerEntryId = 1L

    /** When true, the flows throw, as Room's do when the database can't be opened. */
    var failLoads = false

    /**
     * When true, every function that changes progress throws, as Room's do when the database
     * can't be opened or the disk is full.
     */
    var failSaves = false

    override fun observeAllProgress(): Flow<List<BadgeProgressDetails>> =
        loadedBadges().map { all -> all.values.sortedBy { it.badge.badgeId } }

    override fun observeProgress(badgeId: String): Flow<BadgeProgressDetails?> =
        loadedBadges().map { it[badgeId] }

    private fun loadedBadges() = badges.onStart { if (failLoads) throw IOException("Load failed") }

    override suspend fun startBadge(
        badgeId: String,
        requirementsVersion: LocalDate,
        startedDate: LocalDate
    ) {
        checkCanSave()
        badges.update { all ->
            if (badgeId in all) {
                all
            } else {
                all + (
                    badgeId to BadgeProgressDetails(
                        BadgeProgress(badgeId, requirementsVersion, startedDate),
                        emptyList(),
                        emptyList()
                    )
                    )
            }
        }
    }

    override suspend fun setCounselor(badgeId: String, counselor: Counselor?) {
        checkCanSave()
        updateBadge(badgeId) {
            it.copy(badge = it.badge.copy(counselor = counselor?.normalized()))
        }
    }

    override suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate?) {
        checkCanSave()
        updateBadge(badgeId) { it.copy(badge = it.badge.copy(completedOnPriorDate = date)) }
    }

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?
    ) {
        checkCanSave()
        updateRequirement(badgeId, number) {
            it.copy(completed = true, completedDate = completedDate)
        }
    }

    override suspend fun markRequirementNotCompleted(badgeId: String, number: String) {
        checkCanSave()
        updateRequirement(badgeId, number) { it.copy(completed = false, completedDate = null) }
    }

    override suspend fun setRequirementComment(badgeId: String, number: String, comment: String?) {
        checkCanSave()
        updateRequirement(badgeId, number) { it.copy(comment = comment?.ifBlank { null }) }
    }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        values: Map<String, String>
    ): Long {
        checkCanSave()
        requireStarted(badgeId)
        val id = nextTrackerEntryId++
        updateBadge(badgeId) {
            it.copy(trackerEntries = it.trackerEntries + TrackerEntry(id, badgeId, number, values))
        }
        return id
    }

    override suspend fun updateTrackerEntry(id: Long, values: Map<String, String>) {
        checkCanSave()
        updateEachBadge { details ->
            details.copy(
                trackerEntries = details.trackerEntries.map {
                    if (it.id == id) it.copy(values = values) else it
                }
            )
        }
    }

    override suspend fun deleteTrackerEntry(id: Long) {
        checkCanSave()
        updateEachBadge { details ->
            details.copy(trackerEntries = details.trackerEntries.filterNot { it.id == id })
        }
    }

    override suspend fun clearRequirement(badgeId: String, number: String) {
        checkCanSave()
        if (badgeId !in badges.value) return
        updateBadge(badgeId) { details ->
            details.copy(
                requirements = details.requirements.filterNot { it.requirementNumber == number },
                trackerEntries = details.trackerEntries.filterNot { it.requirementNumber == number }
            )
        }
    }

    override suspend fun clearBadge(badgeId: String) {
        checkCanSave()
        badges.update { it - badgeId }
    }

    override suspend fun clearAll() {
        checkCanSave()
        badges.value = emptyMap()
    }

    private fun checkCanSave() {
        if (failSaves) throw IOException("Save failed")
    }

    private fun requireStarted(badgeId: String) {
        if (badgeId !in badges.value) throw notStartedError(badgeId)
    }

    /** Changes a started badge; throws, as Room does, if it hasn't been started. */
    private fun updateBadge(
        badgeId: String,
        change: (BadgeProgressDetails) -> BadgeProgressDetails
    ) {
        requireStarted(badgeId)
        badges.update { all -> all + (badgeId to change(all.getValue(badgeId))) }
    }

    private fun updateEachBadge(change: (BadgeProgressDetails) -> BadgeProgressDetails) {
        badges.update { all -> all.mapValues { change(it.value) } }
    }

    private fun updateRequirement(
        badgeId: String,
        number: String,
        change: (RequirementProgress) -> RequirementProgress
    ) {
        updateBadge(badgeId) { details ->
            val current = details.requirements.find { it.requirementNumber == number }
                ?: RequirementProgress(badgeId, number)
            details.copy(
                requirements = details.requirements.filterNot { it.requirementNumber == number } +
                    change(current)
            )
        }
    }
}
