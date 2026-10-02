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

    override suspend fun setCounselor(badgeId: String, counselor: Counselor?, start: BadgeStart) {
        checkCanSave()
        updateBadge(badgeId, start) {
            it.copy(badge = it.badge.copy(counselor = counselor?.normalized()))
        }
    }

    override suspend fun setCompletedOnPriorDate(
        badgeId: String,
        date: LocalDate,
        start: BadgeStart
    ) {
        checkCanSave()
        updateBadge(badgeId, start) {
            it.copy(badge = it.badge.copy(completedOnPriorDate = date))
        }
    }

    override suspend fun removeCompletedOnPriorDate(badgeId: String) {
        checkCanSave()
        if (badgeId !in badges.value) return
        updateBadge(badgeId) { it.copy(badge = it.badge.copy(completedOnPriorDate = null)) }
    }

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?,
        start: BadgeStart
    ) {
        checkCanSave()
        updateRequirement(badgeId, number, start) {
            it.copy(completed = true, completedDate = completedDate)
        }
    }

    override suspend fun markRequirementNotCompleted(
        badgeId: String,
        number: String
    ): RequirementProgress? {
        checkCanSave()
        if (badgeId !in badges.value) return null
        return updateRequirement(badgeId, number) {
            it.copy(completed = false, completedDate = null)
        }
    }

    override suspend fun setRequirementCompletedDate(
        badgeId: String,
        number: String,
        date: LocalDate?
    ) {
        checkCanSave()
        badges.update { all ->
            val details = all[badgeId] ?: return@update all
            val requirements = details.requirements.map {
                if (it.requirementNumber == number &&
                    it.completed
                ) {
                    it.copy(completedDate = date)
                } else {
                    it
                }
            }
            all + (badgeId to details.copy(requirements = requirements))
        }
    }

    override suspend fun setCompletedFromRowsDate(
        badgeId: String,
        number: String,
        rowCount: Int,
        date: LocalDate?
    ) {
        checkCanSave()
        val entries = badges.value[badgeId]?.trackerEntries.orEmpty()
            .filter { it.requirementNumber == number }
        if (filledRows(entries, rowCount).size < rowCount) return
        updateRequirement(badgeId, number) { it.copy(completed = true, completedDate = date) }
    }

    override suspend fun setRequirementComment(
        badgeId: String,
        number: String,
        comment: String?,
        start: BadgeStart
    ) {
        checkCanSave()
        updateRequirement(badgeId, number, start) { it.copy(comment = normalizedText(comment)) }
    }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        rowNumber: Int?,
        values: Map<String, String>,
        addedDate: LocalDate,
        start: BadgeStart,
        id: Long?
    ): Long {
        checkCanSave()
        val entries = badges.value[badgeId]?.trackerEntries.orEmpty()
        val filled = entries.find { it.id == id } ?: entries.find {
            rowNumber != null && it.requirementNumber == number && it.rowNumber == rowNumber
        }
        if (filled != null) {
            updateBadge(badgeId) { details ->
                details.copy(
                    trackerEntries = details.trackerEntries.map {
                        if (it.id ==
                            filled.id
                        ) {
                            it.copy(
                                values = normalizedTrackerValues(values),
                                addedDate = it.addedDate ?: addedDate
                            )
                        } else {
                            it
                        }
                    }
                )
            }
            return filled.id
        }
        val entry = TrackerEntry(
            nextTrackerEntryId++,
            badgeId,
            number,
            rowNumber,
            normalizedTrackerValues(values),
            addedDate
        )
        updateBadge(badgeId, start) { it.copy(trackerEntries = it.trackerEntries + entry) }
        return entry.id
    }

    override suspend fun deleteTrackerEntry(id: Long) {
        checkCanSave()
        updateEachBadge { details ->
            details.copy(trackerEntries = details.trackerEntries.filterNot { it.id == id })
        }
    }

    override suspend fun clearRequirements(badgeId: String, numbers: Collection<String>) {
        checkCanSave()
        if (badgeId !in badges.value) return
        updateBadge(badgeId) { details ->
            details.copy(
                requirements = details.requirements.filterNot { it.requirementNumber in numbers },
                trackerEntries =
                    details.trackerEntries.filterNot { it.requirementNumber in numbers }
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

    override suspend fun replaceAll(progress: List<BadgeProgressDetails>) {
        checkCanSave()
        badges.value = progress.associate { details ->
            val entries = details.trackerEntries.map { it.copy(id = nextTrackerEntryId++) }
            details.badge.badgeId to details.copy(trackerEntries = entries)
        }
    }

    private fun checkCanSave() {
        if (failSaves) throw IOException("Save failed")
    }

    /**
     * Changes a started badge, or one that [start] starts in the same update. Callers without a
     * [start] check that it's started first.
     */
    private fun updateBadge(
        badgeId: String,
        start: BadgeStart? = null,
        change: (BadgeProgressDetails) -> BadgeProgressDetails
    ) {
        badges.update { all ->
            val details = all[badgeId]
                ?: start?.let {
                    BadgeProgressDetails(it.progress(badgeId), emptyList(), emptyList())
                }
                ?: error("Badge \"$badgeId\" hasn't been started")
            all + (badgeId to change(details))
        }
    }

    private fun updateEachBadge(change: (BadgeProgressDetails) -> BadgeProgressDetails) {
        badges.update { all -> all.mapValues { change(it.value) } }
    }

    /** Changes a requirement's progress and returns it from before, as Room's DAO does. */
    private fun updateRequirement(
        badgeId: String,
        number: String,
        start: BadgeStart? = null,
        change: (RequirementProgress) -> RequirementProgress
    ): RequirementProgress? {
        var before: RequirementProgress? = null
        updateBadge(badgeId, start) { details ->
            before = details.requirements.find { it.requirementNumber == number }
            details.copy(
                requirements = details.requirements.filterNot { it.requirementNumber == number } +
                    change(before ?: RequirementProgress(badgeId, number))
            )
        }
        return before
    }
}
