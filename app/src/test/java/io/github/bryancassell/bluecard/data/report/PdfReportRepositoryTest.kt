package io.github.bryancassell.bluecard.data.report

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Makes reports from fake repositories, with Robolectric's native graphics to lay them out. A
 * fake writes each report's text in place of the PDF, since PdfDocument only runs on a device
 * (PdfDocumentWriterTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfReportRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val newest = LocalDate.of(2026, 1, 1)
    private val today = LocalDate.of(2026, 9, 30)

    private val chess = MeritBadge(
        id = "chess",
        name = "Chess",
        summary = "Our summary of Chess.",
        officialUrl = "https://www.scouting.org/merit-badges/chess/",
        requirementVersions = listOf(
            RequirementsVersion(
                newest,
                listOf(
                    Requirement(
                        "1",
                        "Do two of these.",
                        requiredCount = 2,
                        children = listOf(
                            Requirement("1a", "Play a game."),
                            Requirement("1b", "Solve a puzzle."),
                            Requirement("1c", "Teach a friend.")
                        )
                    )
                )
            )
        )
    )

    private val catalogRepository = FakeCatalogRepository(listOf(chess))
    private val progressRepository = FakeProgressRepository()
    private val profileRepository = FakeProfileRepository(Profile("Alex Scout", "123"))
    private val pdfWriter = FakePdfWriter()

    private val repository = PdfReportRepository(
        context,
        catalogRepository,
        progressRepository,
        profileRepository,
        pdfWriter,
        Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        UnconfinedTestDispatcher()
    )

    // FileProvider keeps each authority's folders from the first time it's used, while
    // Robolectric gives each test new ones. Starting the provider, as the system does when the
    // app starts, makes it read them again.
    @Before
    fun startFileProvider() {
        val authority = PdfReportRepository.fileProviderAuthority(context)
        // As the manifest declares it.
        val info = context.packageManager.resolveContentProvider(authority, 0)!!
        Robolectric.buildContentProvider(FileProvider::class.java).create(info)
    }

    private val documents = "io.github.bryancassell.bluecard.test.documents"

    // Like the provider the system file picker saved to.
    private val documentsProvider = Robolectric.buildContentProvider(
        FolderDocumentsProvider::class.java
    ).create(
        ProviderInfo().apply {
            authority = documents
            exported = true
            grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS
            writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }
    ).get()

    @Before
    fun setUpDocuments() {
        documentsProvider.folder = folder.newFolder("documents")
    }

    /** A document the file picker created, as it does before the report is saved to it. */
    private fun createdDocument(text: String = ""): Pair<Uri, File> {
        val file = File(documentsProvider.folder, "Chess merit badge report.pdf")
        file.writeText(text)
        return DocumentsContract.buildDocumentUri(documents, file.name) to file
    }

    @Before
    fun completeChess() = runTest {
        val start = BadgeStart(newest, LocalDate.of(2026, 3, 1))
        progressRepository.markRequirementCompleted("chess", "1a", LocalDate.of(2026, 4, 1), start)
        progressRepository.markRequirementCompleted("chess", "1b", LocalDate.of(2026, 4, 2), start)
        progressRepository.setRequirementComment("chess", "1c", "Next week.", start)
    }

    private fun Uri.read(): String =
        context.contentResolver.openInputStream(this)!!.use { it.reader().readText() }

    @Test
    fun createReportToShare_writesTheBadgesReport_whereOtherAppsCanReadIt() = runTest {
        val report = repository.createReportToShare("chess")

        assertEquals("content", report.scheme)
        assertEquals("io.github.bryancassell.bluecard.reports", report.authority)
        assertEquals(pdfWriter.lastWritten, report.read())
        val lines = pdfWriter.pages.single().flatMap { it.lines }
        assertEquals(
            listOf("Merit badge report", "Chess", "Scout: Alex Scout", "Unit: 123"),
            lines.take(4)
        )
        assertTrue("Completed on Apr 2, 2026" in lines)
        assertTrue("Created on Sep 30, 2026" in lines)
        assertTrue("Comment: Next week." in lines)
    }

    // The app the scout shares it with shows its name, as an email attachment does.
    @Test
    fun createReportToShare_namesTheFileAfterTheBadge() = runTest {
        val report = repository.createReportToShare("chess")

        val name = context.contentResolver.query(report, null, null, null, null)!!.use {
            it.moveToFirst()
            it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }
        assertEquals("Chess merit badge report.pdf", name)
    }

    @Test
    fun createReportToShare_again_replacesTheBadgesFile() = runTest {
        repository.createReportToShare("chess")
        progressRepository.setRequirementComment(
            "chess",
            "1c",
            "Taught my brother.",
            BadgeStart(newest, today)
        )

        val report = repository.createReportToShare("chess")

        assertTrue("Comment: Taught my brother." in report.read())
        val files = File(context.cacheDir, PdfReportRepository.REPORTS_FOLDER).list()
        assertEquals(listOf("Chess merit badge report.pdf"), files?.toList())
    }

    @Test
    fun saveReport_writesTheBadgesReport_toTheDestination() = runTest {
        val destination = folder.newFile("report.pdf")

        repository.saveReport("chess", Uri.fromFile(destination))

        assertEquals(pdfWriter.lastWritten, destination.readText())
        assertTrue("Chess" in destination.readText())
    }

    @Test
    fun saveReport_toADocument_replacesWhatItHeld() = runTest {
        // Longer than the report, so a report written over it without truncating it would end
        // with the rest of it.
        val (document, file) = createdDocument("An older, longer file. ".repeat(500))

        repository.saveReport("chess", document)

        assertEquals(pdfWriter.lastWritten, file.readText())
    }

    // The scout could send an empty or partial file without noticing that it failed.
    @Test
    fun saveReport_whenTheReportCantBeWritten_deletesTheDocument() = runTest {
        val (document, file) = createdDocument()
        pdfWriter.failWrites = true

        assertThrows<IOException> { repository.saveReport("chess", document) }
        assertFalse(file.exists())
    }

    @Test
    fun saveReport_whenProgressCantBeRead_deletesTheDocument() = runTest {
        val (document, file) = createdDocument()
        progressRepository.failLoads = true

        assertThrows<IOException> { repository.saveReport("chess", document) }
        assertFalse(file.exists())
    }

    // Another app's refusal isn't a mistake in BlueCard, so the scout sees that it failed
    // rather than the app closing.
    @Test
    fun saveReport_whenTheDocumentsProviderRefuses_throwsIOException() = runTest {
        val (document, _) = createdDocument()
        documentsProvider.refuseOpening = true

        assertThrows<IOException> { repository.saveReport("chess", document) }
    }

    @Test
    fun saveReport_whenTheDestinationCantBeOpened_throwsIOException() = runTest {
        // A folder where the file should be.
        val destination = Uri.fromFile(folder.newFolder("report.pdf"))

        assertThrows<IOException> { repository.saveReport("chess", destination) }
    }

    @Test
    fun report_whenItCantBeWritten_throwsIOException() = runTest {
        pdfWriter.failWrites = true

        assertThrows<IOException> { repository.createReportToShare("chess") }
        assertThrows<IOException> {
            repository.saveReport("chess", Uri.fromFile(folder.newFile("report.pdf")))
        }
    }

    @Test
    fun report_whenTheProfileCantBeRead_throwsIOException() = runTest {
        profileRepository.failLoads = true

        assertThrows<IOException> { repository.createReportToShare("chess") }
    }

    @Test
    fun report_whenProgressCantBeRead_throwsIOException() = runTest {
        progressRepository.failLoads = true

        assertThrows<IOException> { repository.createReportToShare("chess") }
    }

    @Test
    fun report_whenTheCatalogCantBeRead_throwsIOException() = runTest {
        catalogRepository.failLoads = true

        assertThrows<IOException> { repository.createReportToShare("chess") }
    }

    // The screen offers a report only for a completed badge, which is started, after
    // Onboarding has saved the profile, so each of these is a bug.
    @Test
    fun report_forBadgeNotStarted_noProfile_orBadgeNotInCatalog_throwsIllegalState() = runTest {
        catalogRepository.badges = listOf(chess, chess.copy(id = "chess-2"))
        assertThrows<IllegalStateException> { repository.createReportToShare("chess-2") }

        assertThrows<IllegalStateException> { repository.createReportToShare("cooking") }

        profileRepository.removeProfile()
        assertThrows<IllegalStateException> { repository.createReportToShare("chess") }
    }

    @Test
    fun report_forBadgeOnVersionMissingFromCatalog_throwsIllegalState() = runTest {
        catalogRepository.badges = listOf(chess.copy(requirementVersions = emptyList()))

        assertThrows<IllegalStateException> { repository.createReportToShare("chess") }
    }

    // The app has only English strings, so a report on a Persian phone is in English, with
    // English digits, as the screens are (ARCHITECTURE.md, UI layer).
    @Config(qualifiers = "fa")
    @Test
    fun report_onPersianPhone_isInTheStringsLanguage() = runTest {
        repository.createReportToShare("chess")

        val lines = pdfWriter.pages.single().flatMap { it.lines }
        assertTrue("Created on Sep 30, 2026" in lines)
        assertTrue("Do 2 of 3" in lines)
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ${T::class.simpleName}, got $error", error is T)
    }

    /** Writes each page's text in place of a PDF, and records the pages it's given. */
    private class FakePdfWriter : ReportPdfWriter {
        /** When true, writing throws, as PdfDocument does when the file can't be written. */
        var failWrites = false

        /** The pages of each report written, in order. */
        val pages = mutableListOf<List<ReportPage>>()

        /** What it wrote last. */
        var lastWritten: String? = null

        override fun write(pages: List<ReportPage>, out: OutputStream) {
            if (failWrites) throw IOException("Write failed")
            this.pages += pages
            val text = pages.joinToString("\n\n") { it.lines.joinToString("\n") }
            out.write(text.toByteArray())
            lastWritten = text
        }
    }
}
