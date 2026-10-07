package io.github.bryancassell.bluecard.data.progress

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import java.time.LocalDate
import java.time.format.DateTimeParseException

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
        Counselor(normalizedText(name), normalizedText(phone), normalizedText(email))
            .takeIf { it != Counselor() }
}

// The longest text each field takes, for the reasons given beside PROFILE_NAME_MAX_LENGTH.

/** Plenty for a counselor's name. */
const val COUNSELOR_NAME_MAX_LENGTH = 100

/** Plenty for a phone number. */
const val COUNSELOR_PHONE_MAX_LENGTH = 50

/** No email address is longer than 254 characters. */
const val COUNSELOR_EMAIL_MAX_LENGTH = 254

/** Plenty for the name, and maybe the position, of who signed off on a rank's requirement. */
const val SIGNED_OFF_BY_MAX_LENGTH = 100

/** Plenty for notes on a requirement ([RequirementProgress.comment]). */
const val NOTES_MAX_LENGTH = 2_000

/** Plenty for a note in a tracker row's text column. */
const val TRACKER_TEXT_MAX_LENGTH = 500

/**
 * As much as requirement notes ([NOTES_MAX_LENGTH]) for a tracker row's multi-line text column,
 * which can hold a diary entry or a list.
 */
const val TRACKER_MULTILINE_TEXT_MAX_LENGTH = NOTES_MAX_LENGTH

/** Longer than any number a scout would log in a tracker row's number column. */
const val TRACKER_NUMBER_MAX_LENGTH = 20

/**
 * Text the scout typed, such as a requirement's comment, as repositories store it: without
 * spaces around it, and null if nothing is left.
 */
fun normalizedText(text: String?): String? = text?.trim()?.ifEmpty { null }

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
    /**
     * Whether the scout marked it complete. For a requirement with own work, it's that work they
     * marked complete, and the requirement is only complete once enough of its sub-requirements
     * are too ([completion]). For one that [completesFromRows], it's that the scout gave the
     * date it was completed on, which doesn't complete it.
     */
    val completed: Boolean = false,
    /** Optional date the scout gave when they marked it [completed]; only set when it is. */
    val completedDate: LocalDate? = null,
    /**
     * The scout's notes on the requirement. The page called them a comment before it called
     * them Notes (#127); the code and database kept the name, so the rename needed no migration.
     */
    val comment: String? = null,
    /** For a rank's requirement, who signed off on it, such as the Scoutmaster (#248). */
    val signedOffBy: String? = null
)

/**
 * One row of a requirement's tracker: values keyed by the catalog's column IDs. A tracker with
 * a fixed number of rows has at most one entry for each row.
 */
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
    // SQLite counts every null as different, so a log can have any number of entries.
    indices = [Index("badgeId", "requirementNumber", "rowNumber", unique = true)]
)
data class TrackerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val badgeId: String,
    val requirementNumber: String,
    /** The row it fills in a tracker with a fixed number of rows, from 1; null in a log. */
    val rowNumber: Int? = null,
    val values: Map<String, String>,
    /**
     * The date the row was first saved, which changing it keeps. Null for a row saved before
     * database version 3 and not changed since; changing it records the date it's changed on.
     */
    val addedDate: LocalDate? = null
)

/**
 * The [entries] of a tracker with [rowCount] rows, by the row they fill. Only a catalog edited
 * during development could leave an entry outside them.
 */
fun filledRows(entries: List<TrackerEntry>, rowCount: Int): Map<Int, TrackerEntry> =
    entries.filter { it.rowNumber in 1..rowCount }.associateBy { it.rowNumber!! }

/**
 * The rows of this tracker with the [entries] recorded for its requirement, each with its
 * number from 1: in a log, each entry in the order it was added; with a fixed number of rows,
 * every row, with its entry, or null if it isn't filled in.
 */
fun TrackerDefinition.numberedRows(entries: List<TrackerEntry>): List<Pair<Int, TrackerEntry?>> {
    val count = rowCount
    if (count == null) {
        return entries.sortedBy { it.id }.mapIndexed { index, entry -> index + 1 to entry }
    }
    val filled = filledRows(entries, count)
    return (1..count).map { it to filled[it] }
}

/**
 * The values of this tracker's [entry] as stored, each with its column, in column order and
 * without the columns it has none for. Both the requirement's page and the PDF report show a
 * row's values this way.
 */
fun TrackerDefinition.columnValues(entry: TrackerEntry): List<Pair<TrackerColumn, String>> =
    columns.mapNotNull { column -> entry.values[column.id]?.let { column to it } }

/**
 * Tracker values as repositories store them: without spaces around each value, and without
 * values that are blank.
 */
fun normalizedTrackerValues(values: Map<String, String>): Map<String, String> =
    values.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }

/**
 * A date column's stored value as a date, or null if it isn't one. The app stores dates as
 * YYYY-MM-DD, but a value from elsewhere may not be, such as one stored while a catalog edited
 * during development had the column as text. It's shown as it is instead.
 */
fun storedDate(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    null
}

/** A started badge with everything recorded for it. */
data class BadgeProgressDetails(
    @Embedded val badge: BadgeProgress,
    @Relation(parentColumn = "badgeId", entityColumn = "badgeId")
    val requirements: List<RequirementProgress>,
    @Relation(parentColumn = "badgeId", entityColumn = "badgeId")
    val trackerEntries: List<TrackerEntry>
)

/**
 * This progress once the requirements numbered in [numbers] are cleared, as
 * [ProgressRepository.clearRequirements] clears them: without their progress or tracker entries.
 */
fun BadgeProgressDetails.withoutRequirements(numbers: Collection<String>) = copy(
    requirements = requirements.filterNot { it.requirementNumber in numbers },
    trackerEntries = trackerEntries.filterNot { it.requirementNumber in numbers }
)
