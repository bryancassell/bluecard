package io.github.bryancassell.bluecard.data.catalog

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

// The bundled merit badge catalog (assets/catalog.json). The format is documented for
// authors in docs/catalog.md.

@Serializable
data class Catalog(val formatVersion: Int, val badges: List<MeritBadge>)

@Serializable
data class MeritBadge(
    /** Stable ID that progress is stored against, such as "personal-fitness". */
    val id: String,
    val name: String,
    /** Our own short description of the badge. */
    val summary: String,
    /** The badge's official Scouting America page. */
    val officialUrl: String,
    val eagleRequired: Boolean = false,
    /** Eagle-required badges that are alternatives to each other share a group. */
    val eagleGroup: String? = null,
    val requirementVersions: List<RequirementsVersion>
)

@Serializable
data class RequirementsVersion(
    @Serializable(with = LocalDateSerializer::class)
    val effectiveDate: LocalDate,
    val requirements: List<Requirement>
)

@Serializable
data class Requirement(
    /** Official number, such as "4b". Unique within its requirements version. */
    val number: String,
    /** Our own one-line summary. */
    val summary: String,
    /** How many children must be done; null means all of them. */
    val requiredCount: Int? = null,
    val children: List<Requirement> = emptyList(),
    val tracker: TrackerDefinition? = null
)

/** Repeated entries a requirement needs, such as a weekly exercise log. */
@Serializable
data class TrackerDefinition(
    val columns: List<TrackerColumn>,
    /** What one row is called, in lowercase, such as "week". Titles capitalize it. */
    val rowLabel: String,
    /** [rowLabel] in the plural, such as "weeks". */
    val rowLabelPlural: String,
    /** A fixed number of rows (for example 12 weeks); null means any number. */
    val rowCount: Int? = null
)

@Serializable
data class TrackerColumn(val id: String, val label: String, val type: TrackerColumnType)

@Serializable
enum class TrackerColumnType {
    @SerialName("date")
    DATE,

    @SerialName("number")
    NUMBER,

    @SerialName("text")
    TEXT
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
