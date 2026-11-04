package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.data.Documents
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.deleteOrWarn
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.data.progress.earnedBadges
import io.github.bryancassell.bluecard.data.progress.standings
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.di.WarningLog
import io.github.bryancassell.bluecard.di.ZonedClock
import io.github.bryancassell.bluecard.text.PhoneNumberFormat
import io.github.bryancassell.bluecard.text.TextRules
import kotlin.random.Random
import kotlin.random.nextULong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okio.Buffer
import okio.Path
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getString

/**
 * What [PdfReportRepository] needs from each platform, besides its PDF writer: a typesetter, and
 * a folder for reports to share, which another app can read them from.
 */
interface ReportPlatform<T : ReportText> {
    /**
     * Compose resources' environment, as the system has it, to read a report's strings in. It's
     * read once for each report, so a report follows a change to the phone's language, and every
     * string in it, and its typesetter, is in one language.
     */
    suspend fun resourceEnvironment(): ResourceEnvironment

    /** A typesetter in the strings' locale, for strings read in [environment]. */
    suspend fun typesetter(environment: ResourceEnvironment): ReportTypesetter<T>

    /** The folder that reports to share are written to, which isn't backed up. */
    val shareFolder: Path

    /** Where another app can read [file], a report in [shareFolder], from. */
    fun shareUri(file: Path): DocumentUri

    companion object {
        /** The name of each platform's [shareFolder]. */
        const val REPORTS_FOLDER = "reports"
    }
}

/**
 * [ReportRepository] that lays reports out with [layOutReport] and [platform]'s typesetter, and
 * writes them with [pdfWriter]. A report to share is written to [ReportPlatform.shareFolder],
 * named after its badge or rank, so each has one file that the next report replaces. Clearing
 * progress doesn't delete it: an app it was shared with, such as an email app that reads it only
 * when it sends, may still need it.
 *
 * Saving runs in [externalScope], so a report finishes saving even if the scout leaves the
 * screen ([runOutlivingCaller]).
 */
class PdfReportRepository<T : ReportText>(
    private val platform: ReportPlatform<T>,
    private val pdfWriter: ReportPdfWriter<T>,
    private val documents: Documents,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val clock: ZonedClock,
    private val textRules: TextRules,
    private val phoneNumberFormat: PhoneNumberFormat,
    private val warningLog: WarningLog,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ReportRepository {
    private val fileSystem = systemFileSystem

    override suspend fun createReportToShare(advancementId: String): DocumentUri? {
        // Before switching threads: iOS reads it on the main thread, which the screens call from.
        val environment = platform.resourceEnvironment()
        return withContext(ioDispatcher) {
            val report = report(advancementId) ?: return@withContext null
            val pages = layOut(report, environment)
            val folder = platform.shareFolder
            fileSystem.createDirectories(folder)
            val title = getString(environment, report.kind.fileNameTitle, report.name)
            val file = folder / reportFileName(title)
            // Written beside the report's file, then renamed over it in one step: an app that an
            // earlier report was shared with, such as an email app that reads it only to send it,
            // never reads a partly written one, and a report that fails leaves that one as it was.
            // Named at random, so two reports written at once each have their own.
            val written = folder / "report${Random.nextULong()}.tmp"
            try {
                fileSystem.write(written, mustCreate = true) { pdfWriter.write(pages, this) }
                fileSystem.atomicMove(written, file)
            } catch (e: Exception) {
                fileSystem.deleteOrWarn(written, warningLog, TAG)
                throw e
            }
            platform.shareUri(file)
        }
    }

    override suspend fun saveReport(advancementId: String, destination: DocumentUri) =
        externalScope.runOutlivingCaller { save(advancementId, destination) }

    /**
     * Writes the report to [destination] ([Documents.save]), or nothing when there's no report
     * ([report]), as when the badge or rank was just cleared.
     */
    private suspend fun save(advancementId: String, destination: DocumentUri) {
        val environment = platform.resourceEnvironment()
        documents.save(destination) {
            report(advancementId)?.let { report ->
                val pages = layOut(report, environment)
                Buffer().also { pdfWriter.write(pages, it) }.readByteArray()
            }
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
        checkNotNull(report) { "The catalog has no requirements for $advancementId" }
        val phone = report.counselor?.phone ?: return report
        return report.copy(counselorPhone = phoneNumberFormat.format(phone))
    }

    private suspend fun layOut(
        report: AdvancementReport,
        environment: ResourceEnvironment
    ): List<ReportPage<T>> = layOutReport(report, environment, platform.typesetter(environment))

    private companion object {
        const val TAG = "Report"
    }
}
