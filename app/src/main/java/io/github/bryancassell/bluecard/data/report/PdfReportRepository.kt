package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.text.stringsLanguageResources
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "PdfReportRepository"

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
        // Read for each report, so it follows a change to the phone's language.
        val strings = stringsLanguageResources(context)
        val folder = File(context.cacheDir, REPORTS_FOLDER)
        // If the folder can't be made, opening the file throws an IOException.
        folder.mkdirs()
        val file = File(folder, reportFileName(strings, report.badgeName))
        file.outputStream().use { write(report, strings, it) }
        FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    }

    override suspend fun saveReport(badgeId: String, destination: Uri) = withContext(ioDispatcher) {
        try {
            val report = report(badgeId)
            openForWriting(destination).use {
                write(report, stringsLanguageResources(context), it)
            }
        } catch (e: IOException) {
            // The file picker made the document before the report was written, so a report
            // that couldn't be written doesn't leave an empty or partial one behind for the
            // scout to send.
            delete(destination)
            throw e
        }
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

    private fun write(report: BadgeReport, strings: Resources, out: OutputStream) {
        pdfWriter.write(layOutReport(report, strings), out)
    }

    /**
     * Opens [destination], truncating it: with "w", a provider may leave the end of a longer file
     * that was there before (ContentResolver.openOutputStream). The app that holds the
     * destination, such as a cloud drive, can refuse with an exception that isn't an
     * IOException, such as a SecurityException. That's no mistake in BlueCard's code, so it's
     * reported as one.
     */
    private fun openForWriting(destination: Uri): OutputStream = try {
        context.contentResolver.openOutputStream(destination, "wt")
    } catch (e: RuntimeException) {
        throw IOException("Couldn't open $destination", e)
    } ?: throw IOException("Couldn't open $destination: its provider recently crashed")

    /**
     * Deletes [destination], if its provider lets it. If it doesn't, the report's own failure
     * is what the scout is told about, so this one is only logged.
     */
    private fun delete(destination: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, destination)
        } catch (e: Exception) {
            // deleteDocument rethrows whatever the provider throws, such as an
            // UnsupportedOperationException when it can't delete.
            Log.w(TAG, "Couldn't delete the report that failed", e)
        }
    }

    companion object {
        /** The cache folder that reports to share are written to. */
        const val REPORTS_FOLDER = "reports"

        /** The authority of the `FileProvider` that shares reports, set in the manifest. */
        fun fileProviderAuthority(context: Context) = "${context.packageName}.reports"
    }
}
