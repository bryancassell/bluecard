package io.github.bryancassell.bluecard.data.report

import android.content.res.Resources
import android.net.Uri
import io.github.bryancassell.bluecard.R

/**
 * Badge reports: a PDF of everything the scout recorded for a badge, to share or save.
 *
 * A report can only be made for a badge that's in the catalog, by a scout who has saved their
 * profile: its functions throw an [IllegalStateException] otherwise. For a badge that isn't
 * started, as when its progress was cleared just before, they make no report. They throw an
 * `IOException` when the scout's data can't be read or the report can't be written.
 */
interface ReportRepository {
    /**
     * Writes badge [badgeId]'s report to the app's cache, and returns a content URI that
     * another app can read it from once it's granted permission, or null for a badge that isn't
     * started.
     */
    suspend fun createReportToShare(badgeId: String): Uri?

    /**
     * Writes badge [badgeId]'s report to [destination], such as a document the scout chose. It
     * finishes even if the caller is cancelled, such as when the scout leaves the screen. For a
     * badge that isn't started, it deletes [destination] if it's empty, as the file picker
     * creates it, and leaves it as it was otherwise.
     */
    suspend fun saveReport(badgeId: String, destination: Uri)
}

/**
 * The file name of the report on the badge named [badgeName], such as "Camping merit badge
 * report.pdf", from [resources] in the strings' language. Both a shared report and the name the
 * file picker suggests use it.
 *
 * A character that a file name can't hold on a phone, SD card or computer, such as "/", is
 * replaced with "_", as the file picker replaces it when it saves a file (AOSP's
 * `FileUtils.buildValidFatFilename`). Otherwise a badge or translation with a "/" would name a
 * folder that doesn't exist, and the report couldn't be shared.
 */
fun reportFileName(resources: Resources, badgeName: String): String =
    resources.getString(R.string.report_file_name, badgeName)
        .replace(INVALID_FILE_NAME_CHARACTERS, "_") + ".pdf"

/** Control characters and those a FAT file system can't hold, as in AOSP's `FileUtils`. */
private val INVALID_FILE_NAME_CHARACTERS = Regex("""[\x00-\x1F\x7F"*/:<>?\\|]""")
