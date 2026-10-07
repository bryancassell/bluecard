package io.github.bryancassell.bluecard.data.report

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.FakeCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.Rank
import io.github.bryancassell.bluecard.data.catalog.Requirement
import io.github.bryancassell.bluecard.data.catalog.RequirementsVersion
import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.BadgeStart
import io.github.bryancassell.bluecard.data.progress.FakeProgressRepository
import io.github.bryancassell.bluecard.testing.FolderDocumentsProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // As the app's scope, which outlives the screens.
    private val externalScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private fun newRepository(
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        externalScope: CoroutineScope = this.externalScope
    ) = PdfReportRepository(
        context,
        catalogRepository,
        progressRepository,
        profileRepository,
        pdfWriter,
        Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
        ioDispatcher,
        externalScope
    )

    private val repository = newRepository()

    @After
    fun cancelExternalScope() {
        externalScope.cancel()
    }

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

    /** The file name that an app this is shared with shows, as an email attachment does. */
    private fun Uri.displayName(): String =
        context.contentResolver.query(this, null, null, null, null)!!.use {
            it.moveToFirst()
            it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        }

    @Test
    fun createReportToShare_writesTheBadgesReport_whereOtherAppsCanReadIt() = runTest {
        val report = repository.createReportToShare("chess")!!

        assertEquals("content", report.scheme)
        // Local tests run against the debug build, whose application ID ends in ".debug".
        assertEquals("io.github.bryancassell.bluecard.debug.reports", report.authority)
        assertEquals(pdfWriter.lastWritten, report.read())
        val lines = pdfWriter.pages.single().flatMap { it.lines }
        assertEquals(
            listOf("Merit badge report", "Chess", "Scout: Alex Scout", "Unit: 123"),
            lines.take(4)
        )
        assertTrue("Completed on Apr 2, 2026" in lines)
        assertTrue("Created on Sep 30, 2026" in lines)
        assertTrue("Notes: Next week." in lines)
    }

    @Test
    fun createReportToShare_namesTheFileAfterTheBadge() = runTest {
        val report = repository.createReportToShare("chess")!!

        assertEquals("Chess merit badge report.pdf", report.displayName())
    }

    // In the file's name, a "/" would name a folder that doesn't exist.
    @Test
    fun createReportToShare_forBadgeNameWithSlash_sharesIt() = runTest {
        catalogRepository.badges = listOf(chess.copy(name = "Search/Rescue"))

        val report = repository.createReportToShare("chess")!!

        assertEquals(pdfWriter.lastWritten, report.read())
        assertTrue("Search/Rescue" in pdfWriter.pages.single().flatMap { it.lines })
        assertEquals("Search_Rescue merit badge report.pdf", report.displayName())
    }

    // In a translation as well as in the badge's name, as the file picker replaces them, so the
    // file can be kept on an SD card or computer too.
    @Test
    fun reportFileName_replacesEachCharacterAFileNameCantHold() {
        val app = context.resources

        // Resources has no other way to give a string that isn't in the app.
        @Suppress("DEPRECATION")
        val translation = object : Resources(app.assets, app.displayMetrics, app.configuration) {
            override fun getString(id: Int, vararg formatArgs: Any?): String =
                "a\"b*c/d:e<f>g?h\\i|j\tk\u007Fl %1\$s".format(*formatArgs)
        }

        assertEquals(
            "a_b_c_d_e_f_g_h_i_j_k_l Search_Rescue.pdf",
            reportFileName(translation, ReportKind.MeritBadge, "Search/Rescue")
        )
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

        val report = repository.createReportToShare("chess")!!

        assertTrue("Notes: Taught my brother." in report.read())
        val files = File(context.cacheDir, PdfReportRepository.REPORTS_FOLDER).list()
        assertEquals(listOf("Chess merit badge report.pdf"), files?.toList())
    }

    // An app the earlier report was shared with, such as an email app, may read it only when
    // it sends it.
    @Test
    fun createReportToShare_again_leavesTheEarlierReportWhole_forAnAppReadingIt() = runTest {
        val earlier = repository.createReportToShare("chess")!!
        val written = pdfWriter.lastWritten
        context.contentResolver.openInputStream(earlier)!!.use { reading ->
            progressRepository.setRequirementComment(
                "chess",
                "1c",
                "Taught my brother.",
                BadgeStart(newest, today)
            )

            repository.createReportToShare("chess")

            assertEquals(written, reading.reader().readText())
        }
    }

    @Test
    fun createReportToShare_whenItCantBeWritten_leavesTheEarlierReport() = runTest {
        val earlier = repository.createReportToShare("chess")!!
        val written = pdfWriter.lastWritten
        pdfWriter.failWrites = true

        assertThrows<IOException> { repository.createReportToShare("chess") }

        assertEquals(written, earlier.read())
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

    // The scout chose to replace an older report in the file picker. Until there's a new one to
    // write over it, it's theirs to keep.
    @Test
    fun saveReport_overAnOlderReport_whenItFailsBeforeWriting_leavesItAsItWas() = runTest {
        val (document, file) = createdDocument("An older report.")

        progressRepository.failLoads = true
        assertThrows<IOException> { repository.saveReport("chess", document) }
        assertEquals("An older report.", file.readText())
        progressRepository.failLoads = false

        pdfWriter.failWrites = true
        assertThrows<IOException> { repository.saveReport("chess", document) }
        assertEquals("An older report.", file.readText())
        pdfWriter.failWrites = false

        documentsProvider.refuseOpening = true
        assertThrows<IOException> { repository.saveReport("chess", document) }
        assertEquals("An older report.", file.readText())
    }

    @Test
    fun saveReport_toAProviderThatDoesntSupportWt_replacesWhatTheDocumentHeld() = runTest {
        val (document, file) = createdDocument("An older, longer file. ".repeat(500))
        documentsProvider.unsupportedModes = setOf("wt")

        repository.saveReport("chess", document)

        assertEquals(pdfWriter.lastWritten, file.readText())
    }

    // Opening it emptied it, so what's left would be empty or partial.
    @Test
    fun saveReport_whenWritingToTheDocumentFails_deletesIt() = runTest {
        val (document, file) = createdDocument("An older report.")
        documentsProvider.failWrites = true

        assertThrows<IOException> { repository.saveReport("chess", document) }
        assertFalse(file.exists())
    }

    // As when the scout leaves the screen right after choosing where to save it.
    @Test
    fun saveReport_finishesEvenIfItsCallerIsCancelled() = runTest {
        val (document, file) = createdDocument()
        // Holds the save until the test runs it, after the caller is cancelled.
        val repository = newRepository(StandardTestDispatcher(testScheduler), backgroundScope)

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.saveReport("chess", document)
        }
        caller.cancel()
        advanceUntilIdle()

        assertEquals(pdfWriter.lastWritten, file.readText())
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

    // As when the scout clears the badge and taps Share report before the page redraws.
    @Test
    fun createReportToShare_forBadgeNotStarted_makesNoReport() = runTest {
        progressRepository.clearBadge("chess")

        assertNull(repository.createReportToShare("chess"))
        assertNull(pdfWriter.lastWritten)
        val files = File(context.cacheDir, PdfReportRepository.REPORTS_FOLDER).list()
        assertEquals(emptyList<String>(), files.orEmpty().toList())
    }

    // As when the scout clears the badge, taps Save report before the page redraws, and picks
    // where to save it.
    @Test
    fun saveReport_forBadgeNotStarted_savesNothing_andDeletesTheEmptyDocument() = runTest {
        progressRepository.clearBadge("chess")
        val (document, file) = createdDocument()

        repository.saveReport("chess", document)

        assertNull(pdfWriter.lastWritten)
        assertFalse(file.exists())
    }

    @Test
    fun saveReport_forBadgeNotStarted_overAnOlderReport_leavesItAsItWas() = runTest {
        progressRepository.clearBadge("chess")
        val (document, file) = createdDocument("An older report.")

        repository.saveReport("chess", document)

        assertEquals("An older report.", file.readText())
    }

    // The screen offers a report only after Onboarding has saved the profile, for a badge in
    // the catalog, so each of these is a bug.
    @Test
    fun report_noProfile_orBadgeNotInCatalog_throwsIllegalState() = runTest {
        assertThrows<IllegalStateException> { repository.createReportToShare("cooking") }

        profileRepository.removeProfile()
        assertThrows<IllegalStateException> { repository.createReportToShare("chess") }
    }

    @Test
    fun report_forBadgeOnVersionMissingFromCatalog_throwsIllegalState() = runTest {
        catalogRepository.badges = listOf(chess.copy(requirementVersions = emptyList()))

        assertThrows<IllegalStateException> { repository.createReportToShare("chess") }
    }

    // Scout asks for one requirement, and Tenderfoot for a merit badge.
    private fun rank(id: String, name: String, requirement: Requirement) = Rank(
        id = id,
        name = name,
        summary = "Our summary of $name.",
        officialUrl = "https://www.scouting.org/$id.pdf",
        requirementVersions = listOf(RequirementsVersion(newest, listOf(requirement)))
    )

    private val scout = rank("scout", "Scout", Requirement("1", "Learn the Scout Oath."))
    private val tenderfoot = rank(
        "tenderfoot",
        "Tenderfoot",
        Requirement("1", "Earn a merit badge.", meritBadges = MeritBadgesNeeded(1, 0))
    )
    private val rankStart = BadgeStart(newest, LocalDate.of(2026, 3, 1))

    @Before
    fun addRanks() {
        catalogRepository.ranks = listOf(scout, tenderfoot)
    }

    @Test
    fun createReportToShare_forAnEarnedRank_writesItsReport_namedAfterTheRank() = runTest {
        progressRepository.markRequirementCompleted(
            "scout",
            "1",
            LocalDate.of(2026, 4, 5),
            rankStart
        )

        val report = repository.createReportToShare("scout")!!

        assertEquals(pdfWriter.lastWritten, report.read())
        val lines = pdfWriter.pages.single().flatMap { it.lines }
        assertEquals(
            listOf("Rank report", "Scout", "Scout: Alex Scout", "Unit: 123"),
            lines.take(4)
        )
        assertTrue("Earned on Apr 5, 2026" in lines)
        assertEquals("Scout rank report.pdf", report.displayName())
    }

    // Chess is complete on Apr 2, from completeChess.
    @Test
    fun rankReport_listsTheMeritBadgesTheScoutCompleted() = runTest {
        progressRepository.markRequirementCompleted(
            "scout",
            "1",
            LocalDate.of(2026, 4, 5),
            rankStart
        )
        progressRepository.startBadge("tenderfoot", newest, LocalDate.of(2026, 3, 1))

        repository.createReportToShare("tenderfoot")

        val lines = pdfWriter.pages.single().flatMap { it.lines }
        assertTrue("Earned on Apr 2, 2026" in lines)
        assertTrue("1 of 1 merit badge" in lines)
        assertTrue("Chess · Completed on Apr 2, 2026" in lines)
    }

    // As when the scout clears or unmarks the rank and taps Share or Save report before the
    // page redraws: Tenderfoot is complete, but Scout isn't earned.
    @Test
    fun forAStartedRankNotEarned_makesNoReport() = runTest {
        progressRepository.startBadge("tenderfoot", newest, LocalDate.of(2026, 3, 1))
        val (document, file) = createdDocument()

        assertNull(repository.createReportToShare("tenderfoot"))
        repository.saveReport("tenderfoot", document)

        assertNull(pdfWriter.lastWritten)
        assertFalse(file.exists())
    }

    // It has a report, though it isn't started.
    @Test
    fun rankCountedAsEarnedWithARankAbove_hasAReport_thatSaysSo() = runTest {
        progressRepository.setCompletedOnPriorDate(
            "tenderfoot",
            LocalDate.of(2025, 8, 1),
            rankStart
        )

        repository.createReportToShare("scout")

        val lines = pdfWriter.pages.single().flatMap { it.lines }
        assertTrue("Counted as earned with Tenderfoot" in lines)
        assertTrue("Not recorded" in lines)
    }

    @Test
    fun rankReport_onVersionMissingFromCatalog_throwsIllegalState() = runTest {
        progressRepository.setCompletedOnPriorDate("scout", LocalDate.of(2025, 8, 1), rankStart)
        catalogRepository.ranks = listOf(scout.copy(requirementVersions = emptyList()))

        assertThrows<IllegalStateException> { repository.createReportToShare("scout") }
    }

    // The app has only English strings, so a report on a Persian phone is in English, with
    // English digits, as the screens are (ARCHITECTURE.md, Language and layout direction).
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
