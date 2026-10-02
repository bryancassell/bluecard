@file:UseSerializers(LocalDateSerializer::class)

package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.catalog.LocalDateSerializer
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.profile.PROFILE_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.UNIT_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_EMAIL_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_NAME_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.COUNSELOR_PHONE_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.NOTES_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TRACKER_MULTILINE_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.normalizedText
import io.github.bryancassell.bluecard.data.progress.normalizedTrackerValues
import io.github.bryancassell.bluecard.data.progress.storedDate
import java.time.LocalDate
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json

/**
 * The format version this app writes, and the newest it reads. Any change to the format, even
 * an added field, needs a new version, so an older app rejects a file it can't read in full
 * rather than importing it without what it doesn't know. So does raising the longest text a
 * field takes (such as [NOTES_MAX_LENGTH]), which import holds a file to.
 */
const val BACKUP_FORMAT_VERSION = 1

// Every field is required, nulls included, and unknown keys fail parsing, as in the catalog.
private val backupJson = Json

// Reads only the format version, skipping the rest.
private val formatVersionJson = Json { ignoreUnknownKeys = true }

/**
 * [backup] as an export's JSON: the format version, the profile, and each started badge with
 * its requirements and tracker entries. Dates are in ISO form, such as "2026-01-01". Tracker
 * entries are listed in the order they were added, in place of their IDs.
 */
fun encodeBackup(backup: Backup): String =
    backupJson.encodeToString(BackupJson.serializer(), backup.toJson())

/**
 * Reads an export's JSON, checking all of it against the format and this app's [catalog]
 * first. It's [BackupReadResult.NewerFormat] if it's from a newer version of the app: in a
 * newer format, or with a badge or requirements version that [catalog] doesn't have. It's
 * [BackupReadResult.Invalid] unless it holds only what the app could have recorded:
 * - Every date is a date, and only a completed requirement has one.
 * - Each badge is listed once, and each of its requirements once.
 * - Each requirement and tracker column is in the badge's requirements version.
 * - A tracker entry fills one of its tracker's rows, which no other entry fills, or none in a
 *   log, and a date column holds a date.
 * - The name and unit number aren't blank, and no text is longer than its field takes (such as
 *   [NOTES_MAX_LENGTH]), so none is cut short when the scout edits it.
 *
 * Text is stored as repositories store what the scout types ([normalizedText]).
 *
 * The JSON is decoded as it's read, never into a tree of the whole file, so a large or deeply
 * nested file that isn't an export can't use up the app's memory or stack.
 */
fun decodeBackup(json: String, catalog: List<MeritBadge>): BackupReadResult {
    // Checked before the rest, which a newer format may lay out differently.
    val version = try {
        formatVersionJson.decodeFromString(FormatVersionReader, json)
    } catch (e: SerializationException) {
        return BackupReadResult.Invalid
    }
    if (version == null || version < BACKUP_FORMAT_VERSION) return BackupReadResult.Invalid
    if (version > BACKUP_FORMAT_VERSION) return BackupReadResult.NewerFormat
    val file = try {
        backupJson.decodeFromString(BackupJson.serializer(), json)
    } catch (e: SerializationException) {
        return BackupReadResult.Invalid
    }
    // A newer catalog can add badges and requirements versions without a new format version.
    val versions = file.badges.map { it.versionIn(catalog) ?: return BackupReadResult.NewerFormat }
    return try {
        BackupReadResult.Valid(file.toBackup(versions))
    } catch (e: InvalidBackupException) {
        BackupReadResult.Invalid
    }
}

/**
 * Reads only a file's format version, or null if it has none. With [formatVersionJson], it
 * skips the rest of the file as it reads it, keeping none of it.
 */
private object FormatVersionReader : DeserializationStrategy<Long?> {
    override val descriptor = buildClassSerialDescriptor("FormatVersion") {
        element<Long>("formatVersion")
    }

    override fun deserialize(decoder: Decoder): Long? = decoder.decodeStructure(descriptor) {
        var version: Long? = null
        // formatVersion is its only element, so each one it finds is that.
        while (decodeElementIndex(descriptor) != CompositeDecoder.DECODE_DONE) {
            version = decodeLongElement(descriptor, 0)
        }
        version
    }
}

@Serializable
private class BackupJson(
    val formatVersion: Int,
    val profile: ProfileJson,
    val badges: List<BadgeJson>
)

@Serializable
private class ProfileJson(val name: String, val unitNumber: String)

@Serializable
private class BadgeJson(
    val badgeId: String,
    val requirementsVersion: LocalDate,
    val startedDate: LocalDate,
    val counselor: CounselorJson?,
    val completedOnPriorDate: LocalDate?,
    val requirements: List<RequirementJson>,
    val trackerEntries: List<TrackerEntryJson>
)

@Serializable
private class CounselorJson(val name: String?, val phone: String?, val email: String?)

/** A requirement's progress. Its [notes] are [RequirementProgress.comment]. */
@Serializable
private class RequirementJson(
    val number: String,
    val completed: Boolean,
    val completedDate: LocalDate?,
    val notes: String?
)

@Serializable
private class TrackerEntryJson(
    val requirementNumber: String,
    val rowNumber: Int?,
    val addedDate: LocalDate?,
    val values: Map<String, String>
)

private fun Backup.toJson() = BackupJson(
    formatVersion = BACKUP_FORMAT_VERSION,
    profile = ProfileJson(profile.name, profile.unitNumber),
    badges = progress.map { details ->
        val badge = details.badge
        BadgeJson(
            badgeId = badge.badgeId,
            requirementsVersion = badge.requirementsVersion,
            startedDate = badge.startedDate,
            counselor = badge.counselor?.let { CounselorJson(it.name, it.phone, it.email) },
            completedOnPriorDate = badge.completedOnPriorDate,
            requirements = details.requirements.map {
                RequirementJson(it.requirementNumber, it.completed, it.completedDate, it.comment)
            },
            // IDs only grow, so this is the order each log lists its entries in.
            trackerEntries = details.trackerEntries.sortedBy { it.id }.map {
                TrackerEntryJson(it.requirementNumber, it.rowNumber, it.addedDate, it.values)
            }
        )
    }
)

/** Thrown while reading a file that breaks one of [decodeBackup]'s rules. */
private class InvalidBackupException : Exception()

private fun requireValid(valid: Boolean) {
    if (!valid) throw InvalidBackupException()
}

/** The requirements version in [catalog] that this badge was started on, or null if none. */
private fun BadgeJson.versionIn(catalog: List<MeritBadge>): RequirementsVersion? =
    catalog.find { it.id == badgeId }
        ?.requirementVersions
        ?.find { it.effectiveDate == requirementsVersion }

/** This file's backup, with each badge's requirements [versions], in the same order. */
private fun BackupJson.toBackup(versions: List<RequirementsVersion>): Backup {
    requireValid(badges.distinctBy { it.badgeId }.size == badges.size)
    val profile = Profile(
        requiredText(profile.name, PROFILE_NAME_MAX_LENGTH),
        requiredText(profile.unitNumber, UNIT_NUMBER_MAX_LENGTH)
    )
    return Backup(profile, badges.zip(versions) { badge, version -> badge.toProgress(version) })
}

private fun BadgeJson.toProgress(version: RequirementsVersion): BadgeProgressDetails {
    val inVersion = version.requirementsByNumber()
    requireValid(requirements.distinctBy { it.number }.size == requirements.size)
    val rows = trackerEntries.filter { it.rowNumber != null }
    requireValid(rows.distinctBy { it.requirementNumber to it.rowNumber }.size == rows.size)
    val counselor = counselor?.let {
        Counselor(
            optionalText(it.name, COUNSELOR_NAME_MAX_LENGTH),
            optionalText(it.phone, COUNSELOR_PHONE_MAX_LENGTH),
            optionalText(it.email, COUNSELOR_EMAIL_MAX_LENGTH)
        ).normalized()
    }
    return BadgeProgressDetails(
        BadgeProgress(badgeId, requirementsVersion, startedDate, counselor, completedOnPriorDate),
        requirements.map { it.toProgress(badgeId, inVersion) },
        trackerEntries.map { it.toEntry(badgeId, inVersion) }
    )
}

private fun RequirementJson.toProgress(
    badgeId: String,
    inVersion: Map<String, Requirement>
): RequirementProgress {
    requireValid(number in inVersion)
    requireValid(completed || completedDate == null)
    val notes = optionalText(notes, NOTES_MAX_LENGTH)
    return RequirementProgress(badgeId, number, completed, completedDate, notes)
}

private fun TrackerEntryJson.toEntry(
    badgeId: String,
    inVersion: Map<String, Requirement>
): TrackerEntry {
    val tracker = inVersion[requirementNumber]?.tracker ?: throw InvalidBackupException()
    val rowCount = tracker.rowCount
    requireValid(
        if (rowCount == null) rowNumber == null else rowNumber != null && rowNumber in 1..rowCount
    )
    val columns = tracker.columns.associate { it.id to it.type }
    requireValid(values.keys.all { it in columns })
    val stored = normalizedTrackerValues(values)
    requireValid(stored.all { (column, value) -> columns.getValue(column).takes(value) })
    return TrackerEntry(
        badgeId = badgeId,
        requirementNumber = requirementNumber,
        rowNumber = rowNumber,
        values = stored,
        addedDate = addedDate
    )
}

/** Whether a column of this type can hold [value], as the scout enters it. */
private fun TrackerColumnType.takes(value: String) = when (this) {
    TrackerColumnType.TEXT -> value.length <= TRACKER_TEXT_MAX_LENGTH

    TrackerColumnType.MULTILINE_TEXT -> value.length <= TRACKER_MULTILINE_TEXT_MAX_LENGTH

    TrackerColumnType.NUMBER -> value.length <= TRACKER_NUMBER_MAX_LENGTH

    // Chosen with a date picker, and stored as YYYY-MM-DD.
    TrackerColumnType.DATE -> storedDate(value) != null
}

/** Every requirement in this version, at any depth, by number. */
private fun RequirementsVersion.requirementsByNumber(): Map<String, Requirement> {
    fun Requirement.andUnder(): List<Requirement> =
        listOf(this) + children.flatMap { it.andUnder() }
    return requirements.flatMap { it.andUnder() }.associateBy { it.number }
}

/**
 * Text the scout typed, as repositories store it ([normalizedText]): null if it's blank. It
 * must fit in its field, which takes [maxLength] characters.
 */
private fun optionalText(text: String?, maxLength: Int): String? =
    normalizedText(text).also { requireValid(it == null || it.length <= maxLength) }

/** Text the scout must have typed, such as their name. */
private fun requiredText(text: String, maxLength: Int): String =
    optionalText(text, maxLength) ?: throw InvalidBackupException()
