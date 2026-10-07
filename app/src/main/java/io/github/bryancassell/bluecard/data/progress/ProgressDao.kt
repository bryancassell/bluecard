package io.github.bryancassell.bluecard.data.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Transaction
    @Query("SELECT * FROM badge_progress ORDER BY badgeId")
    fun observeAll(): Flow<List<BadgeProgressDetails>>

    @Transaction
    @Query("SELECT * FROM badge_progress WHERE badgeId = :badgeId")
    fun observe(badgeId: String): Flow<BadgeProgressDetails?>

    @Query("SELECT EXISTS(SELECT 1 FROM badge_progress WHERE badgeId = :badgeId)")
    suspend fun isStarted(badgeId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadge(badge: BadgeProgress)

    @Query(
        """
        UPDATE badge_progress
        SET counselor_name = :name, counselor_phone = :phone, counselor_email = :email
        WHERE badgeId = :badgeId
        """
    )
    suspend fun updateCounselor(badgeId: String, name: String?, phone: String?, email: String?)

    @Query("UPDATE badge_progress SET completedOnPriorDate = :date WHERE badgeId = :badgeId")
    suspend fun updateCompletedOnPriorDate(badgeId: String, date: LocalDate?)

    @Query(
        "SELECT * FROM requirement_progress WHERE badgeId = :badgeId AND requirementNumber = :number"
    )
    suspend fun getRequirement(badgeId: String, number: String): RequirementProgress?

    // @Upsert rather than SQL "ON CONFLICT DO UPDATE": that syntax needs SQLite 3.24.0
    // (June 2018, https://www.sqlite.org/lang_upsert.html), which Android 8 (API 26-27,
    // released in 2017) can't include.
    @Upsert
    suspend fun upsertRequirement(requirement: RequirementProgress)

    /**
     * Reads, changes and writes one requirement's progress in a single transaction. Returns
     * the progress from before, or null if nothing was recorded for the requirement.
     */
    @Transaction
    suspend fun updateRequirement(
        badgeId: String,
        number: String,
        change: (RequirementProgress) -> RequirementProgress
    ): RequirementProgress? {
        val current = getRequirement(badgeId, number)
        upsertRequirement(change(current ?: RequirementProgress(badgeId, number)))
        return current
    }

    @Query(
        """
        UPDATE requirement_progress SET completedDate = :date
        WHERE badgeId = :badgeId AND requirementNumber = :number AND completed = 1
        """
    )
    suspend fun updateCompletedDate(badgeId: String, number: String, date: LocalDate?)

    @Query("SELECT * FROM tracker_entry WHERE badgeId = :badgeId AND requirementNumber = :number")
    suspend fun getTrackerEntries(badgeId: String, number: String): List<TrackerEntry>

    /**
     * Marks requirement [number] completed on [date], or with no date, in a single transaction,
     * if each of its tracker's [rowCount] rows has an entry. Its sign-off and comment stay.
     */
    @Transaction
    suspend fun updateCompletedFromRowsDate(
        badgeId: String,
        number: String,
        rowCount: Int,
        date: LocalDate?
    ) {
        if (filledRows(getTrackerEntries(badgeId, number), rowCount).size < rowCount) return
        updateRequirement(badgeId, number) { it.copy(completed = true, completedDate = date) }
    }

    @Insert
    suspend fun insertTrackerEntry(entry: TrackerEntry): Long

    @Query(
        """
        SELECT id FROM tracker_entry
        WHERE badgeId = :badgeId AND requirementNumber = :number AND rowNumber = :rowNumber
        """
    )
    suspend fun getTrackerRowId(badgeId: String, number: String, rowNumber: Int): Long?

    /**
     * Gives the entry already there for [entry]'s row [entry]'s values, or inserts [entry] with
     * a new ID, in a single transaction. Returns the entry's ID. The entry already there is
     * entry [TrackerEntry.id], if it's set and still there, or else for a row of a fixed-row
     * tracker, the entry that fills it. It keeps its [TrackerEntry.addedDate], or gets
     * [entry]'s if it has none.
     */
    @Transaction
    suspend fun addTrackerEntry(entry: TrackerEntry): Long {
        if (entry.id != 0L && updateTrackerEntry(entry.id, entry.values, entry.addedDate) == 1) {
            return entry.id
        }
        val filled = entry.rowNumber?.let {
            getTrackerRowId(entry.badgeId, entry.requirementNumber, it)
        } ?: return insertTrackerEntry(entry.copy(id = 0))
        updateTrackerEntry(filled, entry.values, entry.addedDate)
        return filled
    }

    /**
     * Gives entry [id] these [values], and [addedDate] if it has no date, as an entry saved
     * before database version 3 doesn't. Returns the number of entries it changed: 1, or 0 if
     * there's no entry [id].
     */
    @Query(
        """
        UPDATE tracker_entry SET `values` = :values, addedDate = COALESCE(addedDate, :addedDate)
        WHERE id = :id
        """
    )
    suspend fun updateTrackerEntry(
        id: Long,
        values: Map<String, String>,
        addedDate: LocalDate?
    ): Int

    @Query("DELETE FROM tracker_entry WHERE id = :id")
    suspend fun deleteTrackerEntry(id: Long)

    /** Clears the requirements' dates, sign-offs, comments and tracker entries. */
    @Transaction
    suspend fun deleteRequirements(badgeId: String, numbers: Collection<String>) {
        deleteRequirementProgress(badgeId, numbers)
        deleteTrackerEntries(badgeId, numbers)
    }

    @Query(
        "DELETE FROM requirement_progress " +
            "WHERE badgeId = :badgeId AND requirementNumber IN (:numbers)"
    )
    suspend fun deleteRequirementProgress(badgeId: String, numbers: Collection<String>)

    @Query(
        "DELETE FROM tracker_entry WHERE badgeId = :badgeId AND requirementNumber IN (:numbers)"
    )
    suspend fun deleteTrackerEntries(badgeId: String, numbers: Collection<String>)

    /** Deletes a badge's progress; its requirement progress and tracker entries cascade. */
    @Query("DELETE FROM badge_progress WHERE badgeId = :badgeId")
    suspend fun deleteBadge(badgeId: String)

    @Query("DELETE FROM badge_progress")
    suspend fun deleteAll()

    /**
     * Deletes all progress and inserts [progress] in its place, in a single transaction.
     * Tracker entries are inserted with new IDs, in the order they're listed.
     */
    @Transaction
    suspend fun replaceAll(progress: List<BadgeProgressDetails>) {
        deleteAll()
        insertAll(progress)
    }

    /**
     * Adds each badge in [progress] that isn't started, and puts each one in [replacing] in
     * place of the one started, in a single transaction. Other badges stay as they are. Tracker
     * entries are inserted with new IDs, in the order they're listed.
     */
    @Transaction
    suspend fun merge(progress: List<BadgeProgressDetails>, replacing: Set<String>) {
        val kept = startedBadgeIds().toSet() - replacing
        val added = progress.filter { it.badge.badgeId !in kept }
        deleteBadges(added.map { it.badge.badgeId })
        insertAll(added)
    }

    @Query("SELECT badgeId FROM badge_progress")
    suspend fun startedBadgeIds(): List<String>

    /** Deletes these badges' progress; their requirement progress and tracker entries cascade. */
    @Query("DELETE FROM badge_progress WHERE badgeId IN (:badgeIds)")
    suspend fun deleteBadges(badgeIds: Collection<String>)

    /** Inserts [progress], giving its tracker entries new IDs in the order they're listed. */
    suspend fun insertAll(progress: List<BadgeProgressDetails>) {
        insertBadges(progress.map { it.badge })
        insertRequirements(progress.flatMap { it.requirements })
        // An ID of 0 has Room generate one.
        insertTrackerEntries(progress.flatMap { it.trackerEntries }.map { it.copy(id = 0) })
    }

    @Insert
    suspend fun insertBadges(badges: List<BadgeProgress>)

    @Insert
    suspend fun insertRequirements(requirements: List<RequirementProgress>)

    @Insert
    suspend fun insertTrackerEntries(entries: List<TrackerEntry>)
}
