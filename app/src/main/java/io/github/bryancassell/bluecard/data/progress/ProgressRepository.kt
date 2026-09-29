package io.github.bryancassell.bluecard.data.progress

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Everything the scout records about their badges.
 *
 * A badge must be started with [startBadge] before anything is recorded for it: the
 * other functions that record progress throw [IllegalStateException] for a badge that
 * hasn't been started. Clearing, and changing or deleting a tracker entry that doesn't
 * exist, do nothing.
 *
 * Its flows throw an `IOException` when stored progress can't be read, such as when the
 * database can't be opened. Its other functions don't report storage failures as an
 * `IOException` yet, so catching one won't catch a failed write (see ARCHITECTURE.md,
 * Data layer).
 */
interface ProgressRepository {
    /** Every started badge, with its progress, updated whenever anything changes. */
    fun observeAllProgress(): Flow<List<BadgeProgressDetails>>

    /** One badge's progress, or null if it hasn't been started. */
    fun observeProgress(badgeId: String): Flow<BadgeProgressDetails?>

    /** Starts a badge on a requirements version. Does nothing if it's already started. */
    suspend fun startBadge(badgeId: String, requirementsVersion: LocalDate, startedDate: LocalDate)

    /** Sets the counselor. Blank fields are dropped; a counselor with none left is removed. */
    suspend fun setCounselor(badgeId: String, counselor: Counselor?)

    /** Marks the badge completed on [date] without requirement detail, or undoes it (null). */
    suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate?)

    suspend fun markRequirementCompleted(badgeId: String, number: String, completedDate: LocalDate?)

    /** Undoes completion and removes the completion date; the comment stays. */
    suspend fun markRequirementNotCompleted(badgeId: String, number: String)

    /** Sets the requirement's comment; null or blank removes it. */
    suspend fun setRequirementComment(badgeId: String, number: String, comment: String?)

    /** Adds a tracker row and returns its ID. */
    suspend fun addTrackerEntry(badgeId: String, number: String, values: Map<String, String>): Long

    suspend fun updateTrackerEntry(id: Long, values: Map<String, String>)

    suspend fun deleteTrackerEntry(id: Long)

    /** Clears one requirement's completion, date, comment and tracker entries. */
    suspend fun clearRequirement(badgeId: String, number: String)

    /** Clears everything recorded for a badge, including its counselor. */
    suspend fun clearBadge(badgeId: String)

    /** Clears all progress. The scout's profile is stored elsewhere and stays. */
    suspend fun clearAll()
}

/** What [ProgressRepository] implementations throw when a badge hasn't been started. */
fun notStartedError(badgeId: String) =
    IllegalStateException("Badge \"$badgeId\" hasn't been started")
