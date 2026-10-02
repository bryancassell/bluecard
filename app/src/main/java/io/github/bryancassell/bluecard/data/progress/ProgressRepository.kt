package io.github.bryancassell.bluecard.data.progress

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Everything the scout records about their badges.
 *
 * A badge must be started before anything is recorded for it, with [startBadge] or with the
 * [BadgeStart] that the functions that record progress take and start it with. Undoing a
 * completion ([removeCompletedOnPriorDate], [markRequirementNotCompleted]) does nothing for a
 * badge that hasn't been started, and clearing a badge or requirements with nothing recorded,
 * and changing or deleting a tracker entry that doesn't exist, do nothing too.
 *
 * Its flows throw an `IOException` when stored progress can't be read, such as when the
 * database can't be opened, and its other functions throw one when progress can't be saved.
 * A change finishes even if the caller is cancelled, such as when the scout leaves the screen
 * that made it, and changes are made in the order they're called.
 */
interface ProgressRepository {
    /** Every started badge, with its progress, updated whenever anything changes. */
    fun observeAllProgress(): Flow<List<BadgeProgressDetails>>

    /** One badge's progress, or null if it hasn't been started. */
    fun observeProgress(badgeId: String): Flow<BadgeProgressDetails?>

    /** Starts a badge on a requirements version. Does nothing if it's already started. */
    suspend fun startBadge(badgeId: String, requirementsVersion: LocalDate, startedDate: LocalDate)

    /**
     * Sets the counselor, stored as [Counselor.normalized]: spaces around each field are trimmed,
     * empty fields are dropped, and a counselor with none left is removed. A badge that hasn't
     * been started is started with [start], as in [markRequirementCompleted].
     */
    suspend fun setCounselor(badgeId: String, counselor: Counselor?, start: BadgeStart)

    /**
     * Marks the badge completed on [date] without requirement detail, as for a badge earned
     * before the scout used the app, or changes the date it's marked with. A badge that hasn't
     * been started is started with [start], as in [markRequirementCompleted].
     */
    suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate, start: BadgeStart)

    /**
     * Undoes [setCompletedOnPriorDate]. The badge stays started, with what's recorded for it.
     * Does nothing for a badge that isn't started, as when its progress was cleared just before.
     */
    suspend fun removeCompletedOnPriorDate(badgeId: String)

    /**
     * Marks the requirement completed on [completedDate], or with no date (null). A badge that
     * hasn't been started is started with [start], in the same transaction, so a failure
     * leaves neither.
     */
    suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?,
        start: BadgeStart
    )

    /**
     * Undoes completion and removes the completion date; the comment stays. Returns the
     * requirement's progress from before, read in the same transaction, or null if nothing was
     * recorded for it, so a caller can bring its date back. Does nothing for a badge that isn't
     * started, as when its progress was cleared just before.
     */
    suspend fun markRequirementNotCompleted(badgeId: String, number: String): RequirementProgress?

    /**
     * Changes the date a completed requirement was completed on, or removes it (null). Does
     * nothing if the requirement isn't completed, so a date change that lands just after the
     * scout unchecked it can't complete it again.
     */
    suspend fun setRequirementCompletedDate(badgeId: String, number: String, date: LocalDate?)

    /**
     * Sets the requirement's comment, stored as [normalizedText]: null or blank removes it.
     * A badge that hasn't been started is started with [start], as in
     * [markRequirementCompleted].
     */
    suspend fun setRequirementComment(
        badgeId: String,
        number: String,
        comment: String?,
        start: BadgeStart
    )

    /**
     * Saves a row of requirement [number]'s tracker, in a single write, and returns its entry's
     * ID. [rowNumber] is the row it fills in a tracker with a fixed number of rows, from 1, or
     * null in a log. A row that already has an entry gets these values instead: entry [id], if
     * given and still there, or in a fixed-row tracker the entry that fills [rowNumber], so
     * saving a row twice doesn't add two. Otherwise the row is added, even for an [id] deleted
     * in the meantime, and its new entry's ID is higher than any entry's before it, even a
     * deleted one's. An added row records [addedDate] as the date it was first saved
     * ([TrackerEntry.addedDate]). A row that already has an entry keeps its own, or records
     * [addedDate] if it has none, as a row saved before database version 3 doesn't. The values
     * are stored as [normalizedTrackerValues]. A badge that hasn't been started is started with
     * [start], as in [markRequirementCompleted].
     */
    suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        rowNumber: Int?,
        values: Map<String, String>,
        addedDate: LocalDate,
        start: BadgeStart,
        id: Long? = null
    ): Long

    suspend fun deleteTrackerEntry(id: Long)

    /**
     * Clears the completion, date, comment and tracker entries of each requirement numbered in
     * [numbers], in one transaction. The badge stays started.
     */
    suspend fun clearRequirements(badgeId: String, numbers: Collection<String>)

    /** Clears everything recorded for a badge, including its counselor. */
    suspend fun clearBadge(badgeId: String)

    /** Clears all progress. The scout's profile is stored elsewhere and stays. */
    suspend fun clearAll()

    /**
     * Replaces all progress with [progress], in one transaction, as an import does. It must be
     * shaped as [observeAllProgress] gives it: each badge once, each with only its own
     * requirements, once each, and its own tracker entries, at most one for each row of a
     * fixed-row tracker. It's stored as it is, without the cleanup the other functions do.
     * Tracker entries get new IDs, ignoring theirs, in the order they're listed, so each log
     * keeps its order, and each is higher than any entry's before.
     */
    suspend fun replaceAll(progress: List<BadgeProgressDetails>)
}
