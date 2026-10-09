@file:UseSerializers(LocalDateSerializer::class)

package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.catalog.Advancement
import io.github.bryancassell.bluecard.data.catalog.LocalDateSerializer
import io.github.bryancassell.bluecard.data.catalog.Rank
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
import io.github.bryancassell.bluecard.data.progress.SIGNED_OFF_BY_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_MULTILINE_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_NUMBER_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TRACKER_TEXT_MAX_LENGTH
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.normalizedText
import io.github.bryancassell.bluecard.data.progress.normalizedTrackerValues
import io.github.bryancassell.bluecard.data.progress.storedDate
import io.github.bryancassell.bluecard.data.progress.storedNumber
import io.github.bryancassell.bluecard.text.lineBreaksAsSpaces
import java.time.LocalDate
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
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
 * field takes (such as [NOTES_MAX_LENGTH]), which import holds a file to. Version 2 added who
 * signed off on a rank's requirement ([RequirementJson.signedOffBy]).
 */
const val BACKUP_FORMAT_VERSION = 2

/** The oldest format version this app reads. */
private const val OLDEST_BACKUP_FORMAT_VERSION = 1

// Every field is required, nulls included, and unknown keys fail parsing, as in the catalog.
private val backupJson = Json

// The same for a version 1 file, except that a missing field that can be null reads as null, so
// its requirements read without a sign-off, which it didn't have. That also takes a version 1
// file missing another such field, which only a hand-edited file could be, as a smaller cost than
// a reader of its own: a class for the old layout would leave the serialization plugin's
// constructor and encoder for it untested, and a tree of each requirement can overflow the
// stack. explicitNulls is experimental: a kotlinx.serialization update that renames or removes it
// fails the build, and one that changes what it does fails
// decodeBackup_ofAVersion1Export_readsItWithoutSignOffs.
@OptIn(ExperimentalSerializationApi::class)
private val backupJsonV1 = Json { explicitNulls = false }

// Reads only the format version, skipping the rest.
private val formatVersionJson = Json { ignoreUnknownKeys = true }

/**
 * [backup] as an export's JSON: the format version, the profile, and each started badge with
 * its requirements and tracker entries. A started rank is listed with the badges, as its progress
 * is stored with theirs, so ranks needed no new format version. Dates are in ISO form, such as
 * "2026-01-01". Tracker entries are listed in the order they were added, in place of their IDs.
 */
fun encodeBackup(backup: Backup): String =
    backupJson.encodeToString(BackupJson.serializer(), backup.toJson())

/**
 * Reads an export's JSON, checking all of it against the format and this app's [catalog]
 * first. It's [BackupReadResult.NewerFormat] if it's from a newer version of the app: in a
 * newer format, or with a badge, rank or requirements version that [catalog] doesn't have. It's
 * [BackupReadResult.Invalid] unless it holds only what the app could have recorded:
 * - Every date is a date, and only a completed requirement has one.
 * - Only a rank's requirement has a sign-off.
 * - Each badge or rank is listed once, and each of its requirements once.
 * - Each requirement and tracker column is in its badge's or rank's requirements version.
 * - A tracker entry fills one of its tracker's rows, which no other entry fills, or none in a
 *   log. It has a value once cleaned up. A date column holds a date, and a number column a
 *   number.
 * - The name and unit number aren't blank, and no text is longer than its field takes (such as
 *   [NOTES_MAX_LENGTH]), so none is cut short when the scout edits it.
 *
 * The app's fields never save a date column value that isn't a date, or a number column value
 * that isn't a number, and only a catalog edited during development can leave one. So rejecting
 * it keeps the scout's exports importable without changing a file's data unseen (#233). A
 * tracker entry without a value is rejected for the same reason (#239). The one export from
 * before single-line fields replaced pasted line breaks that this rejects has a row whose only
 * value is a pasted next-line character, which becomes a space. Only a development build can
 * have one.
 *
 * Text is cleaned up as the app does when the scout saves it: it's trimmed ([normalizedText]),
 * and a single-line text field's line breaks are spaces, as the field replaces them
 * ([lineBreaksAsSpaces]). A one-line field never shows a line break, so a space keeps the text
 * the scout sees, and an export saved before those fields replaced pasted line breaks still
 * imports (#156). A field's length limit applies to the text as it's stored.
 *
 * A file in an older format version imports without what was added since, such as a version 1
 * file without sign-offs (#248).
 *
 * The JSON is decoded as it's read, never into a tree of the whole file, so a large or deeply
 * nested file that isn't an export can't use up the app's memory or stack.
 */
fun decodeBackup(json: String, catalog: List<Advancement>): BackupReadResult {
    // Checked before the rest, which a newer format may lay out differently.
    val version = try {
        formatVersionJson.decodeFromString(FormatVersionReader, json)
    } catch (e: SerializationException) {
        return BackupReadResult.Invalid
    }
    if (version == null || version < OLDEST_BACKUP_FORMAT_VERSION) return BackupReadResult.Invalid
    if (version > BACKUP_FORMAT_VERSION) return BackupReadResult.NewerFormat
    // Version 1 had no sign-off.
    val withoutSignOffs = version == 1L
    val file = try {
        val format = if (withoutSignOffs) backupJsonV1 else backupJson
        format.decodeFromString(BackupJson.serializer(), json)
    } catch (e: SerializationException) {
        return BackupReadResult.Invalid
    }
    if (withoutSignOffs && file.badges.any { it.requirements.any { it.signedOffBy != null } }) {
        return BackupReadResult.Invalid
    }
    // A newer catalog can add badges, ranks and requirements versions without a new format
    // version.
    val started = file.badges.map { it.startedIn(catalog) ?: return BackupReadResult.NewerFormat }
    return try {
        BackupReadResult.Valid(file.toBackup(started))
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
    val notes: String?,
    val signedOffBy: String?
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
                RequirementJson(
                    it.requirementNumber,
                    it.completed,
                    it.completedDate,
                    it.comment,
                    it.signedOffBy
                )
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

/** A badge or rank in the catalog, and the requirements version it was started on. */
private class StartedOn(val advancement: Advancement, val version: RequirementsVersion)

/** What this badge or rank was started on in [catalog], or null if [catalog] doesn't have it. */
private fun BadgeJson.startedIn(catalog: List<Advancement>): StartedOn? {
    val advancement = catalog.find { it.id == badgeId } ?: return null
    val version = advancement.requirementVersions.find { it.effectiveDate == requirementsVersion }
    return version?.let { StartedOn(advancement, it) }
}

/** This file's backup, with what each badge was [started] on, in the same order. */
private fun BackupJson.toBackup(started: List<StartedOn>): Backup {
    requireValid(badges.distinctBy { it.badgeId }.size == badges.size)
    val profile = Profile(
        requiredText(profile.name, PROFILE_NAME_MAX_LENGTH),
        requiredText(profile.unitNumber, UNIT_NUMBER_MAX_LENGTH)
    )
    return Backup(profile, badges.zip(started) { badge, on -> badge.toProgress(on) })
}

private fun BadgeJson.toProgress(started: StartedOn): BadgeProgressDetails {
    val inVersion = started.version.requirementsByNumber()
    val isRank = started.advancement is Rank
    requireValid(requirements.distinctBy { it.number }.size == requirements.size)
    val rows = trackerEntries.filter { it.rowNumber != null }
    requireValid(rows.distinctBy { it.requirementNumber to it.rowNumber }.size == rows.size)
    val counselor = counselor?.let {
        Counselor(
            singleLineText(it.name, COUNSELOR_NAME_MAX_LENGTH),
            singleLineText(it.phone, COUNSELOR_PHONE_MAX_LENGTH),
            singleLineText(it.email, COUNSELOR_EMAIL_MAX_LENGTH)
        ).normalized()
    }
    return BadgeProgressDetails(
        BadgeProgress(badgeId, requirementsVersion, startedDate, counselor, completedOnPriorDate),
        requirements.map { it.toProgress(badgeId, inVersion, isRank) },
        trackerEntries.map { it.toEntry(badgeId, inVersion) }
    )
}

private fun RequirementJson.toProgress(
    badgeId: String,
    inVersion: Map<String, Requirement>,
    isRank: Boolean
): RequirementProgress {
    requireValid(number in inVersion)
    requireValid(completed || completedDate == null)
    // Only a rank's requirement page has the field, so a badge's never has one, even blank.
    requireValid(isRank || signedOffBy == null)
    val notes = optionalText(notes, NOTES_MAX_LENGTH)
    val signedOffBy = singleLineText(signedOffBy, SIGNED_OFF_BY_MAX_LENGTH)
    return RequirementProgress(badgeId, number, completed, completedDate, notes, signedOffBy)
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
    // A text column's field is single-line, as singleLineText describes.
    val typed = values.mapValues { (column, value) ->
        if (columns.getValue(column) == TrackerColumnType.TEXT) lineBreaksAsSpaces(value) else value
    }
    val stored = normalizedTrackerValues(typed)
    // The app can't save a row with nothing in its fields: Save is off while they're all empty
    // (#239).
    requireValid(stored.isNotEmpty())
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

    // The field takes digits with at most one decimal separator, and a number without a digit
    // isn't saved, so what's stored is a number as storedNumber reads it. A lone "." is rejected
    // rather than left out as saving does: dropping it without dropping other values that
    // aren't numbers would take a rule of its own (#233).
    TrackerColumnType.NUMBER ->
        value.length <= TRACKER_NUMBER_MAX_LENGTH && storedNumber(value) != null

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

/**
 * Text the scout typed in a single-line field, such as a counselor's name: [optionalText] with
 * each line break a space, as the field replaces them ([lineBreaksAsSpaces]).
 */
private fun singleLineText(text: String?, maxLength: Int): String? =
    optionalText(text?.let(::lineBreaksAsSpaces), maxLength)

/** Single-line text the scout must have typed, such as their name. */
private fun requiredText(text: String, maxLength: Int): String =
    singleLineText(text, maxLength) ?: throw InvalidBackupException()
