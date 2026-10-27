package io.github.bryancassell.bluecard.data.backup

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import kotlinx.datetime.LocalDate

/**
 * Export and import of everything the scout entered: their profile and all their progress, in
 * one JSON file ([encodeBackup]).
 *
 * Its functions throw an `IOException` when the scout's data can't be read or saved, or the
 * file can't be read or written.
 */
interface BackupRepository {
    /**
     * Writes the profile and all progress to [destination], such as a document the scout
     * chose. It finishes even if the caller is cancelled, such as when the scout leaves the
     * screen. Throws an [IllegalStateException] if the scout hasn't saved their profile.
     */
    suspend fun exportBackup(destination: DocumentUri)

    /** Reads the file at [source] and checks whether it can be imported, changing nothing. */
    suspend fun readBackup(source: DocumentUri): BackupReadResult

    /**
     * Replaces the profile and all progress with [backup]'s. It finishes even if the caller is
     * cancelled. If it fails, the progress may already be replaced, but not the profile.
     */
    suspend fun importBackup(backup: Backup)

    /**
     * Merges [backup] into the scout's data: each of its badges and ranks that isn't started is
     * added, and each in [fromFile] replaces the scout's progress on it, as
     * [ProgressRepository.merge] does. The scout's progress on any other stays. The profile is
     * replaced with [backup]'s if [profileFromFile]. It finishes even if the caller is
     * cancelled. If it fails, the progress may already be merged, but the profile not replaced.
     */
    suspend fun mergeBackup(backup: Backup, fromFile: Set<String>, profileFromFile: Boolean)
}

/**
 * The name the file picker suggests for an export made on [date], such as "BlueCard export
 * 2026-10-01.json", from [format], `export_file_name` as written. The date is in ISO form, so
 * the name has no slashes in it and files sort by date.
 *
 * It fills in "%1$s" itself, as `stringResource` would. The name is made as the scout taps
 * Export, so the tap's guard against a quick second tap engages at once (`OtherAppStarter`), and
 * outside composition Compose Multiplatform fills in a string only in the suspend `getString`.
 */
fun exportFileName(format: String, date: LocalDate): String =
    format.replace("%1\$s", date.toString()) + ".json"
