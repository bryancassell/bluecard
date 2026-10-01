package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import io.github.bryancassell.bluecard.di.IoDispatcher
import io.github.bryancassell.bluecard.text.stringsLanguageResources
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
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

private const val TAG = "PdfReportRepository"

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
    override suspend fun createReportToShare(badgeId: String): Uri = withContext(ioDispatcher) {
        val report = report(badgeId)
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
     * Writes the report to [destination]. The report is made before the destination is opened,
     * which empties it, so a report that can't be made leaves a file the scout chose to replace
     * as it was.
     */
    private suspend fun save(badgeId: String, destination: Uri) {
        val pdf: ByteArray
        val out: OutputStream
        try {
            pdf = ByteArrayOutputStream().also {
                write(report(badgeId), stringsLanguageResources(context), it)
            }.toByteArray()
            out = openForWriting(destination)
        } catch (e: Exception) {
            // The file picker made an empty document for the report, which isn't left behind.
            deleteIfEmpty(destination)
            throw e
        }
        try {
            out.use { it.write(pdf) }
        } catch (e: Exception) {
            // Opening it emptied it, so it's empty or partial, and the scout could send it
            // without noticing that it failed.
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
     * that was there before (ContentResolver.openOutputStream). A provider that doesn't support
     * "wt" is asked for "rwt": Google Drive refused "wt" with a FileNotFoundException, but
     * truncated with "rwt" (https://issuetracker.google.com/issues/180526528), and
     * DocumentsProvider.openDocument says to refuse a mode with an UnsupportedOperationException.
     *
     * The app that holds the destination, such as a cloud drive, can refuse with an exception
     * that isn't an IOException, such as a SecurityException. That's no mistake in BlueCard's
     * code, so it's reported as one.
     */
    private fun openForWriting(destination: Uri): OutputStream = try {
        try {
            context.contentResolver.openOutputStream(destination, "wt")
        } catch (e: FileNotFoundException) {
            context.contentResolver.openOutputStream(destination, "rwt")
        } catch (e: UnsupportedOperationException) {
            context.contentResolver.openOutputStream(destination, "rwt")
        }
    } catch (e: RuntimeException) {
        throw IOException("Couldn't open $destination", e)
    } ?: throw IOException("Couldn't open $destination: its provider recently crashed")

    /**
     * Deletes [destination] if its provider says it's empty, as the file picker makes it. If it
     * doesn't say, it's kept, in case it's a file the scout chose to replace.
     */
    private fun deleteIfEmpty(destination: Uri) {
        val size = try {
            // The form of query that providers take since Android O: DocumentsProvider refuses
            // the older one.
            val columns = arrayOf(OpenableColumns.SIZE)
            context.contentResolver.query(destination, columns, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
        } catch (e: Exception) {
            // As in delete.
            Log.w(TAG, "Couldn't find the size of the report that failed", e)
            null
        }
        if (size == 0L) delete(destination)
    }

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
