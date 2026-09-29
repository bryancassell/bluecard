package io.github.bryancassell.bluecard.data.progress

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

// What the scout records, stored in Room. Badges and requirements are identified by
// catalog IDs: a badge's ID and a requirement's official number within the badge's
// requirements version.

/** A badge the scout has started. Its requirement progress and tracker entries hang off it. */
@Entity(tableName = "badge_progress")
data class BadgeProgress(
    @PrimaryKey val badgeId: String,
    /** Effective date of the requirements version this badge is worked on. */
    val requirementsVersion: LocalDate,
    val startedDate: LocalDate,
    @Embedded(prefix = "counselor_") val counselor: Counselor? = null,
    /** Set when the scout marked the badge completed on a date without requirement detail. */
    val completedOnPriorDate: LocalDate? = null
)

/**
 * How to start a badge that hasn't been started, when something is recorded for it: on a
 * requirements version (its effective date), on a date. See [ProgressRepository].
 */
data class BadgeStart(val requirementsVersion: LocalDate, val startedDate: LocalDate) {
    /** The progress of badge [badgeId] when it's started this way. */
    fun progress(badgeId: String) = BadgeProgress(badgeId, requirementsVersion, startedDate)
}

/** The badge's merit badge counselor. Every field is optional. */
data class Counselor(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null
) {
    /**
     * This counselor with spaces around each field trimmed and empty fields removed, or null if
     * no field is left. Room reads a counselor with every field empty back as null, so
     * repositories store this form.
     */
    fun normalized(): Counselor? =
        Counselor(name.trimmedOrNull(), phone.trimmedOrNull(), email.trimmedOrNull())
            .takeIf { it != Counselor() }

    private fun String?.trimmedOrNull() = this?.trim()?.ifEmpty { null }
}

/**
 * A requirement's comment as repositories store it: without spaces around it, and null if
 * nothing is left.
 */
fun normalizedComment(comment: String?): String? = comment?.trim()?.ifEmpty { null }

@Entity(
    tableName = "requirement_progress",
    primaryKeys = ["badgeId", "requirementNumber"],
    foreignKeys = [
        ForeignKey(
            entity = BadgeProgress::class,
            parentColumns = ["badgeId"],
            childColumns = ["badgeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RequirementProgress(
    val badgeId: String,
    val requirementNumber: String,
    val completed: Boolean = false,
    /** Optional date the requirement was completed; only set when [completed]. */
    val completedDate: LocalDate? = null,
    val comment: String? = null
)

/** One row of a requirement's tracker: values keyed by the catalog's column IDs. */
@Entity(
    tableName = "tracker_entry",
    foreignKeys = [
        ForeignKey(
            entity = BadgeProgress::class,
            parentColumns = ["badgeId"],
            childColumns = ["badgeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("badgeId", "requirementNumber")]
)
data class TrackerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val badgeId: String,
    val requirementNumber: String,
    val values: Map<String, String>
)

/** A started badge with everything recorded for it. */
data class BadgeProgressDetails(
    @Embedded val badge: BadgeProgress,
    @Relation(parentColumn = "badgeId", entityColumn = "badgeId")
    val requirements: List<RequirementProgress>,
    @Relation(parentColumn = "badgeId", entityColumn = "badgeId")
    val trackerEntries: List<TrackerEntry>
)
