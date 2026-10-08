package io.github.bryancassell.bluecard.data.catalog

import java.text.Collator
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

// The bundled catalog of merit badges and ranks (assets/catalog.json). The format is documented
// for authors in docs/catalog.md.

@Serializable
data class Catalog(
    val formatVersion: Int,
    val badges: List<MeritBadge>,
    /** The ranks, Scout through Eagle, in the order they're earned. */
    val ranks: List<Rank>
)

/**
 * A merit badge or a rank: the scout earns it by completing its requirements, which the
 * requirement pages, completion and trackers handle the same way for both.
 */
sealed interface Advancement {
    /**
     * Stable ID that progress is stored against, such as "personal-fitness". Unique across
     * badges and ranks, because their progress is stored in the same tables.
     */
    val id: String
    val name: String

    /** Our own short description. */
    val summary: String

    /** Its official Scouting America page. */
    val officialUrl: String
    val requirementVersions: List<RequirementsVersion>
}

@Serializable
data class MeritBadge(
    override val id: String,
    override val name: String,
    override val summary: String,
    override val officialUrl: String,
    val eagleRequired: Boolean = false,
    /** Eagle-required badges that are alternatives to each other share a group. */
    val eagleGroup: String? = null,
    override val requirementVersions: List<RequirementsVersion>
) : Advancement

@Serializable
data class Rank(
    override val id: String,
    override val name: String,
    override val summary: String,
    override val officialUrl: String,
    override val requirementVersions: List<RequirementsVersion>
) : Advancement

@Serializable
data class RequirementsVersion(
    @Serializable(with = LocalDateSerializer::class)
    val effectiveDate: LocalDate,
    val requirements: List<Requirement>
)

@Serializable
data class Requirement(
    /**
     * Official number, such as "4b", or for a rank's parent the PDF leaves out, the number its
     * lettered requirements imply, such as Scout "1". Unique within its requirements version.
     */
    val number: String,
    /** Our own one-line summary. */
    val summary: String,
    /** How many children must be done; null means all of them. */
    val requiredCount: Int? = null,
    val children: List<Requirement> = emptyList(),
    val tracker: TrackerDefinition? = null,
    /**
     * Our own one-line summary of the work it asks for besides its children, such as a course
     * to take before them, or null if it asks for none. Only a requirement with children has
     * one.
     */
    val ownWork: String? = null,
    /**
     * For a rank's requirement that asks for time in the rank below, such as Star 1's four
     * months as First Class, the months it asks for, or null. It shows when the scout becomes
     * eligible, but doesn't complete the requirement.
     */
    val monthsInRank: Int? = null,
    /**
     * For a rank's requirement that asks for merit badges, such as Star 3's six, how many and
     * how many of them Eagle-required, or null. It's complete once the scout has completed
     * enough badges, so they don't mark it by hand.
     */
    val meritBadges: MeritBadgesNeeded? = null
) {
    /** How many of its children must be done. */
    val neededCount: Int get() = requiredCount ?: children.size

    /** How many of its children must be done when that's fewer than all of them, or null. */
    val choiceCount: Int? get() = requiredCount?.takeIf { it < children.size }
}

/**
 * How many merit badges a rank's requirement asks the scout to have completed in all ([total]),
 * and how many of them must be Eagle-required ([eagleRequired]).
 */
@Serializable
data class MeritBadgesNeeded(
    val total: Int,
    val eagleRequired: Int,
    /**
     * Whether the badges of an Eagle "one of" group count once toward [eagleRequired]
     * ([eagleSlots]), as for Eagle 3, which needs one of each group. Otherwise every
     * Eagle-required badge counts, as for Star 3 and Life 3.
     */
    val eagleGroupsCountOnce: Boolean = false
)

/**
 * The order badges are listed in. Badge names are English whatever the device language, so
 * they're sorted by English rules: an accented letter sorts with its base letter, and case
 * only breaks ties.
 */
fun badgeNameOrder(): Comparator<MeritBadge> =
    compareBy(Collator.getInstance(Locale.ENGLISH), MeritBadge::name)

/**
 * The Eagle-required badges in this catalog, one list for each badge the scout needs: a badge
 * required on its own, or the badges of a "one of" group ([MeritBadge.eagleGroup]), such as
 * Cycling, Hiking and Swimming, any of which meets the requirement.
 */
fun List<MeritBadge>.eagleSlots(): List<List<MeritBadge>> {
    val (grouped, single) = filter { it.eagleRequired }.partition { it.eagleGroup != null }
    return single.map { listOf(it) } + grouped.groupBy { it.eagleGroup }.values
}

/** Repeated entries a requirement needs, such as a weekly exercise log. */
@Serializable
data class TrackerDefinition(
    val columns: List<TrackerColumn>,
    /** What one row is called, in lowercase, such as "week". Titles capitalize it. */
    val rowLabel: String,
    /** [rowLabel] in the plural, such as "weeks". */
    val rowLabelPlural: String,
    /** A fixed number of rows (for example 12 weeks); null means any number. */
    val rowCount: Int? = null,
    /**
     * In a log, how many rows its requirement asks for, such as at least 10 animals, or null.
     * It's only a guide: the scout can record more, and still checks the requirement off.
     */
    val rowsNeeded: Int? = null
) {
    /** What one row is called, capitalized for titles such as "Week 3". */
    val rowTitle: String get() = rowLabel.replaceFirstChar { it.titlecase() }

    /** What a count of the rows is out of, as in "8 of 12 weeks": [rowCount] or [rowsNeeded]. */
    val rowsOutOf: Int? get() = rowCount ?: rowsNeeded

    /**
     * What the rows are called in a count of the [recorded] ones, agreeing with the number:
     * "1 session" or "5 sessions" in a log, and "8 of 12 weeks" when the count is out of a number
     * ([rowsOutOf]). The catalog is in English, so its row labels follow English plurals.
     */
    fun rowsLabel(recorded: Int): String =
        if ((rowsOutOf ?: recorded) == 1) rowLabel else rowLabelPlural
}

@Serializable
data class TrackerColumn(
    val id: String,
    val label: String,
    val type: TrackerColumnType,
    /** For a number column, the amount its values add up to that the requirement asks for. */
    val total: ColumnTotal? = null
)

/**
 * An amount a requirement asks for, such as 6 hours of service, that a number column's values
 * add up to. The requirement shows the total against it, such as "4 of 6 hours", but it doesn't
 * complete the requirement.
 */
@Serializable
data class ColumnTotal(
    val needed: Int,
    /** What the amount is counted in, in lowercase, such as "hour". */
    val label: String,
    /** [label] in the plural, such as "hours". */
    val labelPlural: String,
    /**
     * The id of the column whose total this amount is part of, such as Life 4's conservation
     * hours, which its hours include, or null. The progress bar counts that total only as far as
     * this part allows (data/progress/FractionDone.kt).
     */
    val partOf: String? = null
) {
    /**
     * What the amount is counted in, agreeing with the amount needed, as in "0.5 of 1 hour" or
     * "4 of 6 hours". The catalog is in English, so its labels follow English plurals.
     */
    val neededLabel: String get() = if (needed == 1) label else labelPlural
}

@Serializable
enum class TrackerColumnType {
    @SerialName("date")
    DATE,

    @SerialName("number")
    NUMBER,

    @SerialName("text")
    TEXT,

    /** Text of more than one line, such as a description or a list. */
    @SerialName("multiline-text")
    MULTILINE_TEXT
}

/** The format version this app reads. */
const val CATALOG_FORMAT_VERSION = 1

// Unknown keys fail parsing, so a misspelled field name is caught.
private val catalogJson = Json

/** Parses catalog JSON. Throws [kotlinx.serialization.SerializationException] if malformed. */
fun parseCatalog(json: String): Catalog = catalogJson.decodeFromString(json)

/** Reads and writes dates as ISO-8601 strings, such as "2026-01-01". */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        val text = decoder.decodeString()
        return try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            // Report bad dates like any other malformed catalog JSON.
            throw SerializationException("\"$text\" is not a date in YYYY-MM-DD form", e)
        }
    }
}
