@file:UseSerializers(LocalDateSerializer::class)

package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.catalog.LocalDateSerializer
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeProgress
import io.github.bryancassell.bluecard.data.progress.BadgeProgressDetails
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.RequirementProgress
import io.github.bryancassell.bluecard.data.progress.TrackerEntry
import io.github.bryancassell.bluecard.data.progress.normalizedText
import io.github.bryancassell.bluecard.data.progress.normalizedTrackerValues
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The format version this app writes, and the newest it reads. Any change to the format, even
 * an added field, needs a new version, so an older app rejects a file it can't read in full
 * rather than importing it without what it doesn't know.
 */
const val BACKUP_FORMAT_VERSION = 1

/**
 * The longest text the app imports, in any field. Text shown in a text field is saved with
 * the screen's state, which has a size limit (see TextLengthLimit), so a huge value mustn't
 * reach it. This is several times the longest a field takes (Notes, 2,000 characters), so
 * raising a field's limit doesn't make the app reject its own exports.
 */
const val MAX_IMPORTED_TEXT_LENGTH = 10_000

// Every field is required, nulls included, and unknown keys fail parsing, as in the catalog.
private val backupJson = Json

/**
 * [backup] as an export's JSON: the format version, the profile, and each started badge with
 * its requirements and tracker entries. Dates are in ISO form, such as "2026-01-01". Tracker
 * entries are listed in the order they were added, in place of their IDs.
 */
fun encodeBackup(backup: Backup): String =
    backupJson.encodeToString(BackupJson.serializer(), backup.toJson())

/**
 * Reads an export's JSON, checking all of it first. It's [BackupReadResult.Invalid] unless it
 * could have been exported: it's this app's format, every date is a date, a badge or
 * requirement is listed once, a fixed-row tracker's row has one entry, only a completed
 * requirement has a date, the name and unit number aren't blank, and no text is longer than
 * [MAX_IMPORTED_TEXT_LENGTH]. Text is stored as repositories store what the scout types
 * ([normalizedText]).
 */
fun decodeBackup(json: String): BackupReadResult {
    val element = try {
        backupJson.parseToJsonElement(json)
    } catch (e: SerializationException) {
        return BackupReadResult.Invalid
    }
    // Checked before the rest, which a newer format may lay out differently.
    val version = ((element as? JsonObject)?.get("formatVersion") as? JsonPrimitive)
        ?.takeUnless { it.isString }
        ?.longOrNull
    return when {
        version == null || version < BACKUP_FORMAT_VERSION -> BackupReadResult.Invalid

        version > BACKUP_FORMAT_VERSION -> BackupReadResult.NewerFormat

        else -> try {
            BackupReadResult.Valid(
                backupJson.decodeFromJsonElement(BackupJson.serializer(), element).toBackup()
            )
        } catch (e: SerializationException) {
            BackupReadResult.Invalid
        } catch (e: InvalidBackupException) {
            BackupReadResult.Invalid
        }
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

private fun BackupJson.toBackup(): Backup {
    requireValid(badges.distinctBy { it.badgeId }.size == badges.size)
    return Backup(
        Profile(requiredText(profile.name), requiredText(profile.unitNumber)),
        badges.map { it.toProgress() }
    )
}

private fun BadgeJson.toProgress(): BadgeProgressDetails {
    requireValid(isId(badgeId))
    requireValid(requirements.distinctBy { it.number }.size == requirements.size)
    val rows = trackerEntries.filter { it.rowNumber != null }
    requireValid(rows.distinctBy { it.requirementNumber to it.rowNumber }.size == rows.size)
    val counselor = counselor?.let {
        Counselor(optionalText(it.name), optionalText(it.phone), optionalText(it.email))
            .normalized()
    }
    return BadgeProgressDetails(
        BadgeProgress(badgeId, requirementsVersion, startedDate, counselor, completedOnPriorDate),
        requirements.map { it.toProgress(badgeId) },
        trackerEntries.map { it.toEntry(badgeId) }
    )
}

private fun RequirementJson.toProgress(badgeId: String): RequirementProgress {
    requireValid(isId(number))
    requireValid(completed || completedDate == null)
    return RequirementProgress(badgeId, number, completed, completedDate, optionalText(notes))
}

private fun TrackerEntryJson.toEntry(badgeId: String): TrackerEntry {
    requireValid(isId(requirementNumber))
    requireValid(rowNumber == null || rowNumber >= 1)
    requireValid(values.all { (column, value) -> isId(column) && fits(value) })
    return TrackerEntry(
        badgeId = badgeId,
        requirementNumber = requirementNumber,
        rowNumber = rowNumber,
        values = normalizedTrackerValues(values),
        addedDate = addedDate
    )
}

/** Text the scout typed, as repositories store it ([normalizedText]): null if it's blank. */
private fun optionalText(text: String?): String? {
    requireValid(text == null || fits(text))
    return normalizedText(text)
}

/** Text the scout must have typed, such as their name. */
private fun requiredText(text: String): String =
    optionalText(text) ?: throw InvalidBackupException()

/** Whether [id] could be a catalog ID, such as a badge's ID or a requirement's number. */
private fun isId(id: String) = id.isNotBlank() && fits(id)

private fun fits(text: String) = text.length <= MAX_IMPORTED_TEXT_LENGTH
