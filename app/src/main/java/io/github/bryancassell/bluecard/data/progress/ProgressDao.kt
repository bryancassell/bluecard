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

    /** Reads, changes and writes one requirement's progress in a single transaction. */
    @Transaction
    suspend fun updateRequirement(
        badgeId: String,
        number: String,
        change: (RequirementProgress) -> RequirementProgress
    ) {
        val current = getRequirement(badgeId, number) ?: RequirementProgress(badgeId, number)
        upsertRequirement(change(current))
    }

    @Insert
    suspend fun insertTrackerEntry(entry: TrackerEntry): Long

    @Query("UPDATE tracker_entry SET `values` = :values WHERE id = :id")
    suspend fun updateTrackerEntry(id: Long, values: Map<String, String>)

    @Query("DELETE FROM tracker_entry WHERE id = :id")
    suspend fun deleteTrackerEntry(id: Long)

    /** Clears one requirement's date, comment and tracker entries. */
    @Transaction
    suspend fun deleteRequirement(badgeId: String, number: String) {
        deleteRequirementProgress(badgeId, number)
        deleteTrackerEntries(badgeId, number)
    }

    @Query(
        "DELETE FROM requirement_progress WHERE badgeId = :badgeId AND requirementNumber = :number"
    )
    suspend fun deleteRequirementProgress(badgeId: String, number: String)

    @Query("DELETE FROM tracker_entry WHERE badgeId = :badgeId AND requirementNumber = :number")
    suspend fun deleteTrackerEntries(badgeId: String, number: String)

    /** Deletes a badge's progress; its requirement progress and tracker entries cascade. */
    @Query("DELETE FROM badge_progress WHERE badgeId = :badgeId")
    suspend fun deleteBadge(badgeId: String)

    @Query("DELETE FROM badge_progress")
    suspend fun deleteAll()
}
