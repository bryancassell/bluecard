package io.github.bryancassell.bluecard.data.report

import android.net.Uri
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first

/**
 * A [ReportRepository] for other features' tests. It records the reports asked for instead of
 * writing them, since PdfDocument only runs on a device. Like the real one, it makes no report
 * for a badge that [progressRepository] doesn't have started, or for a rank in
 * [catalogRepository] that isn't earned.
 */
class FakeReportRepository(
    private val progressRepository: ProgressRepository,
    private val catalogRepository: CatalogRepository = FakeCatalogRepository()
) : ReportRepository {
    /**
     * When true, its functions throw, as the real repository's do when the scout's data can't
     * be read or the report can't be written.
     */
    var failSaves = false

    /** When set, [createReportToShare] waits for it, as if the report took that long to write. */
    var writing: CompletableDeferred<Unit>? = null

    /** The badges and ranks whose reports were created to share, in order. */
    val shared = mutableListOf<String>()

    /** The badges and ranks whose reports were saved, with where each was saved to, in order. */
    val saved = mutableListOf<Pair<String, Uri>>()

    override suspend fun createReportToShare(advancementId: String): Uri? {
        shared += advancementId
        writing?.await()
        if (failSaves) throw IOException("Save failed")
        return if (hasReport(advancementId)) reportUri(advancementId) else null
    }

    override suspend fun saveReport(advancementId: String, destination: Uri) {
        if (failSaves) throw IOException("Save failed")
        if (hasReport(advancementId)) saved += advancementId to destination
    }

    private suspend fun hasReport(advancementId: String): Boolean {
        val ranks = catalogRepository.getRanks()
        if (ranks.none { it.id == advancementId }) {
            return progressRepository.observeProgress(advancementId).first() != null
        }
        val progress =
            progressRepository.observeAllProgress().first().associateBy { it.badge.badgeId }
        val earnedBadges = catalogRepository.getBadges().earnedBadges(progress)
        return ranks.standings(progress, earnedBadges)
            .first { it.rank.id == advancementId }
            .status == RankStatus.Earned
    }

    companion object {
        /** The URI [createReportToShare] returns for badge or rank [advancementId]'s report. */
        fun reportUri(advancementId: String): Uri =
            Uri.parse("content://reports/$advancementId.pdf")
    }
}
