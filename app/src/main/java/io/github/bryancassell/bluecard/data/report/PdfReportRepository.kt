package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.data.saveDocument
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.text.stringsLanguageResources
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [ReportRepository] that lays reports out with [layOutReport] and writes them with
 * [pdfWriter], which is Android's PdfDocument outside tests. A report to share is written to
 * the cache folder's `reports` folder, which `FileProvider` shares (`res/xml/report_paths.xml`),
 * named after its badge, so each badge has one file that the next report replaces.
 *
 * Saving runs in [externalScope], so a report finishes saving even if the scout leaves the
 * screen ([runOutlivingCaller]).
 */
class PdfReportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val pdfWriter: ReportPdfWriter,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ReportRepository {
    override suspend fun createReportToShare(badgeId: String): Uri? = withContext(ioDispatcher) {
        val report = report(badgeId) ?: return@withContext null
        // Read for each report, so it follows a change to the phone's language.
        val strings = stringsLanguageResources(context)
        val folder = File(context.cacheDir, REPORTS_FOLDER)
        // If the folder can't be made, creating a file in it throws an IOException.
        folder.mkdirs()
        val file = File(folder, reportFileName(strings, report.badgeName))
        // Written beside the badge's file, then renamed over it in one step: an app that an
        // earlier report was shared with, such as an email app that reads it only to send it,
        // never reads a partly written one, and a report that fails leaves that one as it was.
        val written = File.createTempFile("report", null, folder)
        try {
            written.outputStream().use { write(report, strings, it) }
            Files.move(written.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            // Only there if it wasn't moved.
            written.delete()
        }
        FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    }

    override suspend fun saveReport(badgeId: String, destination: Uri) =
        externalScope.runOutlivingCaller {
            withContext(ioDispatcher) { save(badgeId, destination) }
        }

    /**
     * Writes the report to [destination] ([saveDocument]), or nothing for a badge that isn't
     * started, as when it was just cleared.
     */
    private suspend fun save(badgeId: String, destination: Uri) =
        context.contentResolver.saveDocument(destination) {
            report(badgeId)?.let { report ->
                ByteArrayOutputStream().also {
                    write(report, stringsLanguageResources(context), it)
                }.toByteArray()
            }
        }

    /** Badge [badgeId]'s report, or null if it isn't started, as when it was just cleared. */
    private suspend fun report(badgeId: String): BadgeReport? {
        val profile = checkNotNull(profileRepository.observeProfile().first()) {
            "The scout hasn't saved their profile"
        }
        val badge = checkNotNull(catalogRepository.getBadges().find { it.id == badgeId }) {
            "The catalog has no badge $badgeId"
        }
        val progress = progressRepository.observeProgress(badgeId).first() ?: return null
        return checkNotNull(badge.report(profile, progress, LocalDate.now(clock))) {
            "The catalog has no requirements for $badgeId"
        }
    }

    private fun write(report: BadgeReport, strings: Resources, out: OutputStream) {
        pdfWriter.write(layOutReport(report, strings), out)
    }

    companion object {
        /** The cache folder that reports to share are written to. */
        const val REPORTS_FOLDER = "reports"

        /** The authority of the `FileProvider` that shares reports, set in the manifest. */
        fun fileProviderAuthority(context: Context) = "${context.packageName}.reports"
    }
}
