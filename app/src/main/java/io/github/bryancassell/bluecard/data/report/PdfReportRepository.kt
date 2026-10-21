package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.data.saveDocument
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.di.WarningLog
import io.github.bryancassell.bluecard.di.ZonedClock
import io.github.bryancassell.bluecard.text.TextRules
import io.github.bryancassell.bluecard.text.stringsLanguageResources
import kotlin.random.Random
import kotlin.random.nextULong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * [ReportRepository] that lays reports out with [layOutReport] and writes them with
 * [pdfWriter], which is Android's PdfDocument outside tests. A report to share is written to
 * the cache folder's `reports` folder, which `FileProvider` shares (`res/xml/report_paths.xml`),
 * named after its badge or rank, so each has one file that the next report replaces. Clearing
 * progress doesn't delete it: an app it was shared with, such as an email app that reads it only
 * when it sends, may still need it.
 *
 * Saving runs in [externalScope], so a report finishes saving even if the scout leaves the
 * screen ([runOutlivingCaller]).
 */
class PdfReportRepository(
    private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val pdfWriter: ReportPdfWriter,
    private val clock: ZonedClock,
    private val textRules: TextRules,
    private val warningLog: WarningLog,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ReportRepository {
    private val fileSystem = FileSystem.SYSTEM

    override suspend fun createReportToShare(advancementId: String): Uri? =
        withContext(ioDispatcher) {
            val report = report(advancementId) ?: return@withContext null
            // Read for each report, so it follows a change to the phone's language.
            val strings = stringsLanguageResources(context)
            val folder = context.cacheDir.toOkioPath() / REPORTS_FOLDER
            fileSystem.createDirectories(folder)
            val file = folder / reportFileName(strings, report.kind, report.name)
            // Written beside the report's file, then renamed over it in one step: an app that an
            // earlier report was shared with, such as an email app that reads it only to send it,
            // never reads a partly written one, and a report that fails leaves that one as it was.
            // Named at random, so two reports written at once each have their own.
            val written = folder / "report${Random.nextULong()}.tmp"
            try {
                fileSystem.write(written, mustCreate = true) { write(report, strings, this) }
                fileSystem.atomicMove(written, file)
            } finally {
                // Only there if it wasn't moved.
                fileSystem.delete(written)
            }
            FileProvider.getUriForFile(context, fileProviderAuthority(context), file.toFile())
        }

    override suspend fun saveReport(advancementId: String, destination: Uri) =
        externalScope.runOutlivingCaller {
            withContext(ioDispatcher) { save(advancementId, destination) }
        }

    /**
     * Writes the report to [destination] ([saveDocument]), or nothing when there's no report
     * ([report]), as when the badge or rank was just cleared.
     */
    private suspend fun save(advancementId: String, destination: Uri) =
        context.contentResolver.saveDocument(destination, warningLog) {
            report(advancementId)?.let { report ->
                Buffer().also {
                    write(report, stringsLanguageResources(context), it)
                }.readByteArray()
            }
        }

    /**
     * Badge or rank [advancementId]'s report, or null for a badge that isn't started or a rank
     * that isn't earned, as when it was just cleared.
     */
    private suspend fun report(advancementId: String): AdvancementReport? {
        val profile = checkNotNull(profileRepository.observeProfile().first()) {
            "The scout hasn't saved their profile"
        }
        val badges = catalogRepository.getBadges()
        val ranks = catalogRepository.getRanks()
        val today = clock.today()
        val badge = badges.find { it.id == advancementId }
        val report = if (badge != null) {
            val progress = progressRepository.observeProgress(advancementId).first() ?: return null
            badge.report(profile, progress, today)
        } else {
            checkNotNull(ranks.find { it.id == advancementId }) {
                "The catalog has no badge or rank $advancementId"
            }
            // Every rank's, because the ranks below and above it decide whether it's earned, and
            // every badge's, which its requirements that ask for merit badges count.
            val progress = progressRepository.observeAllProgress().first()
                .associateBy { it.badge.badgeId }
            val earnedBadges = badges.earnedBadges(progress)
            val standing = ranks.standings(progress, earnedBadges)
                .first { it.rank.id == advancementId }
            if (standing.status != RankStatus.Earned) return null
            standing.report(profile, progress[advancementId], earnedBadges, textRules, today)
        }
        return checkNotNull(report) { "The catalog has no requirements for $advancementId" }
    }

    private fun write(report: AdvancementReport, strings: Resources, out: BufferedSink) {
        pdfWriter.write(layOutReport(report, strings), out)
    }

    companion object {
        /** The cache folder that reports to share are written to. */
        const val REPORTS_FOLDER = "reports"

        /**
         * The authority of the `FileProvider` that shares reports, set in the manifest. It's
         * built from the application ID, because two installed apps can't declare the same one,
         * and a debug build installs next to a release build.
         */
        fun fileProviderAuthority(context: Context) = "${context.packageName}.reports"
    }
}
