package io.github.bryancassell.bluecard.data.report

import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [ReportRepository] for other features' tests. It records the reports asked for instead of
 * writing them, since PdfDocument only runs on a device.
 */
class FakeReportRepository : ReportRepository {
    /**
     * When true, its functions throw, as the real repository's do when the scout's data can't
     * be read or the report can't be written.
     */
    var failSaves = false

    /** When set, [createReportToShare] waits for it, as if the report took that long to write. */
    var writing: CompletableDeferred<Unit>? = null

    /** The badges whose reports were created to share, in order. */
    val shared = mutableListOf<String>()

    /** The badges whose reports were saved, with where each was saved to, in order. */
    val saved = mutableListOf<Pair<String, Uri>>()

    override suspend fun createReportToShare(badgeId: String): Uri {
        shared += badgeId
        writing?.await()
        if (failSaves) throw IOException("Save failed")
        return reportUri(badgeId)
    }

    override suspend fun saveReport(badgeId: String, destination: Uri) {
        if (failSaves) throw IOException("Save failed")
        saved += badgeId to destination
    }

    companion object {
        /** The URI [createReportToShare] returns for badge [badgeId]'s report. */
        fun reportUri(badgeId: String): Uri = Uri.parse("content://reports/$badgeId.pdf")
    }
}
