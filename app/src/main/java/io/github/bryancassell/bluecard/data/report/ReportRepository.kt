package io.github.bryancassell.bluecard.data.report

import android.content.res.Resources
import android.net.Uri
import io.github.bryancassell.bluecard.R

/**
 * Badge reports: a PDF of everything the scout recorded for a badge, to share or save.
 *
 * A report can only be made for a badge that's in the catalog and started, by a scout who has
 * saved their profile: its functions throw an [IllegalStateException] otherwise. They throw an
 * `IOException` when the scout's data can't be read or the report can't be written.
 */
interface ReportRepository {
    /**
     * Writes badge [badgeId]'s report to the app's cache, and returns a content URI that
     * another app can read it from once it's granted permission.
     */
    suspend fun createReportToShare(badgeId: String): Uri

    /**
     * Writes badge [badgeId]'s report to [destination], such as a document the scout chose. It
     * finishes even if the caller is cancelled, such as when the scout leaves the screen.
     */
    suspend fun saveReport(badgeId: String, destination: Uri)
}

/**
 * The file name of the report on the badge named [badgeName], such as "Camping merit badge
 * report.pdf", from [resources] in the strings' language. Both a shared report and the name the
 * file picker suggests use it.
 */
fun reportFileName(resources: Resources, badgeName: String): String =
    resources.getString(R.string.report_file_name, badgeName) + ".pdf"
