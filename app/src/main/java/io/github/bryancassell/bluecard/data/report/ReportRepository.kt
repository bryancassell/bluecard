package io.github.bryancassell.bluecard.data.report

import android.net.Uri

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

    /** Writes badge [badgeId]'s report to [destination], such as a document the scout chose. */
    suspend fun saveReport(badgeId: String, destination: Uri)
}
