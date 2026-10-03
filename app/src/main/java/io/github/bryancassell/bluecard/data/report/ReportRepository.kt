package io.github.bryancassell.bluecard.data.report

import android.content.res.Resources
import android.net.Uri
import io.github.bryancassell.bluecard.R

/**
 * Badge and rank reports: a PDF of everything the scout recorded for a badge or rank, to share
 * or save.
 *
 * A report can only be made for a badge or rank that's in the catalog, by a scout who has saved
 * their profile: its functions throw an [IllegalStateException] otherwise. For a badge that
 * isn't started or a rank that isn't earned, as when its progress was cleared just before, they
 * make no report. They throw an `IOException` when the scout's data can't be read or the report
 * can't be written.
 */
interface ReportRepository {
    /**
     * Writes badge or rank [advancementId]'s report to the app's cache, and returns a content
     * URI that another app can read it from once it's granted permission, or null when it makes
     * no report.
     */
    suspend fun createReportToShare(advancementId: String): Uri?

    /**
     * Writes badge or rank [advancementId]'s report to [destination], such as a document the
     * scout chose. It finishes even if the caller is cancelled, such as when the scout leaves
     * the screen. When it makes no report, it deletes [destination] if it's empty, as the file
     * picker creates it, and leaves it as it was otherwise.
     */
    suspend fun saveReport(advancementId: String, destination: Uri)
}

/**
 * The file name of the report on the badge or rank named [name], such as "Camping merit badge
 * report.pdf" or "Star rank report.pdf", from [resources] in the strings' language. Both a
 * shared report and the name the file picker suggests use it.
 *
 * A character that a file name can't hold on a phone, SD card or computer, such as "/", is
 * replaced with "_", as the file picker replaces it when it saves a file (AOSP's
 * `FileUtils.buildValidFatFilename`). Otherwise a badge or translation with a "/" would name a
 * folder that doesn't exist, and the report couldn't be shared.
 */
fun reportFileName(resources: Resources, kind: ReportKind, name: String): String {
    val fileName = when (kind) {
        ReportKind.MeritBadge -> R.string.report_file_name
        ReportKind.Rank -> R.string.report_file_name_rank
    }
    return resources.getString(fileName, name).replace(INVALID_FILE_NAME_CHARACTERS, "_") + ".pdf"
}

/** Control characters and those a FAT file system can't hold, as in AOSP's `FileUtils`. */
private val INVALID_FILE_NAME_CHARACTERS = Regex("""[\x00-\x1F\x7F"*/:<>?\\|]""")
