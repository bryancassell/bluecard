package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.ui.stringsLanguageResources
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * [ReportRepository] that lays reports out with [layOutReport] and writes them with
 * [pdfWriter], which is Android's PdfDocument outside tests. A report to share is written to
 * the cache folder's `reports` folder, which `FileProvider` shares (`res/xml/report_paths.xml`),
 * named after its badge, so each badge has one file that the next report replaces.
 */
class PdfReportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val profileRepository: ProfileRepository,
    private val pdfWriter: ReportPdfWriter,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ReportRepository {
    override suspend fun createReportToShare(badgeId: String): Uri = withContext(ioDispatcher) {
        val report = report(badgeId)
        val folder = File(context.cacheDir, REPORTS_FOLDER)
        // If the folder can't be made, opening the file throws an IOException.
        folder.mkdirs()
        val name = strings().getString(R.string.report_file_name, report.badgeName)
        val file = File(folder, "$name.pdf")
        file.outputStream().use { write(report, it) }
        FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    }

    override suspend fun saveReport(badgeId: String, destination: Uri) = withContext(ioDispatcher) {
        val report = report(badgeId)
        val out = context.contentResolver.openOutputStream(destination)
            ?: throw IOException("Couldn't open $destination")
        out.use { write(report, it) }
    }

    private suspend fun report(badgeId: String): BadgeReport {
        val profile = checkNotNull(profileRepository.observeProfile().first()) {
            "The scout hasn't saved their profile"
        }
        val badge = checkNotNull(catalogRepository.getBadges().find { it.id == badgeId }) {
            "The catalog has no badge $badgeId"
        }
        val progress = checkNotNull(progressRepository.observeProgress(badgeId).first()) {
            "Badge $badgeId hasn't been started"
        }
        return checkNotNull(badge.report(profile, progress, LocalDate.now(clock))) {
            "The catalog has no requirements for $badgeId"
        }
    }

    private fun write(report: BadgeReport, out: OutputStream) {
        pdfWriter.write(layOutReport(report, strings()), out)
    }

    // Read each time, so a report follows a change to the phone's language.
    private fun strings() = stringsLanguageResources(context)

    companion object {
        /** The cache folder that reports to share are written to. */
        const val REPORTS_FOLDER = "reports"

        /** The authority of the `FileProvider` that shares reports, set in the manifest. */
        fun fileProviderAuthority(context: Context) = "${context.packageName}.reports"
    }
}
