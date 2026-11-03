@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.text.EnglishLocaleFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import okio.Buffer
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox

/**
 * Writes reports with UIGraphicsPDFRenderer and reads the PDF back with PDFKit, on the iOS
 * simulator, as PdfDocumentWriterTest checks Android's writer on a device.
 */
class UIGraphicsPdfWriterTest {
    private val typesetter = CoreTextTypesetter(EnglishLocaleFormats, false, "en")
    private val note = TrackerColumn("note", "Note", TrackerColumnType.MULTILINE_TEXT)

    // Long enough to take several pages, with a tracker and notes as a real badge has.
    private val report = AdvancementReport(
        profile = Profile("Alex Scout", "123"),
        kind = ReportKind.MeritBadge,
        name = "Camping",
        requirementsVersion = LocalDate(2026, 1, 1),
        completion = Completion(LocalDate(2026, 6, 3)),
        earnedWith = null,
        counselor = Counselor("Pat Lee", "555-0100", "pat@example.com"),
        requirements = (1..40).map { number ->
            ReportRequirement(
                number = "$number",
                summary = "Requirement number $number of the badge.",
                requiredCount = null,
                completion = Completion(LocalDate(2026, 4, 1)),
                notNeeded = false,
                notRecorded = false,
                signedOffBy = null,
                comment = "Done with my patrol on a weekend campout.",
                tracker = ReportTracker(
                    TrackerDefinition(listOf(note), "night", "nights"),
                    listOf(ReportTrackerRow(1, listOf(note to "Slept in a tent."))),
                    emptyList()
                ).takeIf { number % 10 == 0 },
                meritBadges = null,
                children = emptyList()
            )
        },
        createdDate = LocalDate(2026, 9, 30)
    )

    private val pages = runBlocking {
        layOutReport(report, getSystemResourceEnvironment(), typesetter)
    }

    private fun writePdf(pages: List<ReportPage<CoreTextText>>): ByteArray =
        Buffer().also { UIGraphicsPdfWriter().write(pages, it) }.readByteArray()

    private fun read(pdf: ByteArray): PDFDocument {
        val data = pdf.usePinned {
            NSData.create(bytes = it.addressOf(0), length = pdf.size.toULong())
        }
        return checkNotNull(PDFDocument(data)) { "PDFKit can't read the PDF" }
    }

    @Test
    fun write_makesAPdf_withAPageForEachPage_onLetterPaper() {
        val pdf = writePdf(pages)

        assertEquals("%PDF-", pdf.copyOf(5).decodeToString())
        val document = read(pdf)
        assertTrue(pages.size > 1, "Expected several pages, got ${pages.size}")
        assertEquals(pages.size, document.pageCount.toInt())
        repeat(pages.size) { index ->
            document.pageAtIndex(index.toULong())!!.boundsForBox(kPDFDisplayBoxMediaBox)
                .useContents {
                    assertEquals(PAGE_WIDTH.toDouble(), size.width)
                    assertEquals(PAGE_HEIGHT.toDouble(), size.height)
                }
        }
    }

    @Test
    fun write_putsEachPagesText_onThatPageOnly() {
        val document = read(writePdf(pages))

        val written = List(pages.size) {
            document.pageAtIndex(it.toULong())!!.string.orEmpty().withoutSpaces()
        }
        pages.forEachIndexed { index, page ->
            // Spaces can differ in a PDF's text, so they're left out.
            var from = 0
            page.lines.map { it.withoutSpaces() }.forEach { line ->
                val at = written[index].indexOf(line, from)
                assertTrue(at >= 0, "Page ${index + 1} is missing \"$line\" after $from")
                from = at + line.length
            }
        }
        // Each requirement is on one page, once.
        (1..40).forEach { number ->
            val title = "$number. Requirement number $number of the badge.".withoutSpaces()
            val found = written.sumOf { Regex(Regex.escape(title)).findAll(it).count() }
            assertEquals(1, found, "\"$title\" in the PDF")
        }
    }

    // So the renderer's context is UIKit's, as CoreTextText.draw takes it: text drawn upside
    // down or mirrored would still be read as text.
    @Test
    fun write_drawsTheFirstPage_downFromTheTopMargin_withItsNumberAtTheFoot() {
        val page = read(writePdf(pages)).pageAtIndex(0u)!!

        val ink = ink(uiKitCoordinates = false) { page.drawWithBox(kPDFDisplayBoxMediaBox, it) }

        val margin = 54
        val firstLine = pages[0].texts[0].text.lineBottom(0)
        assertTrue(ink.found, "Nothing was drawn")
        assertTrue(ink.top in margin..<margin + firstLine.toInt(), "Ink starts at y ${ink.top}")
        assertTrue(ink.left >= margin, "Ink starts at x ${ink.left}")
        assertTrue(ink.right < PAGE_WIDTH - margin, "Ink ends at x ${ink.right}")
        // The page number, in the bottom margin.
        assertTrue(ink.bottom in PAGE_HEIGHT - margin..<PAGE_HEIGHT, "Ink ends at y ${ink.bottom}")
    }

    private fun String.withoutSpaces() = filterNot { it.isWhitespace() }
}
