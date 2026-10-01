package io.github.bryancassell.bluecard.data.report

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import java.io.File
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes a report with Android's PdfDocument and reads the PDF back with PdfRenderer, on a
 * device, since neither runs in local tests. Run it with `./gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PdfDocumentWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "PdfDocumentWriterTest.pdf")

    private val log = TrackerDefinition(
        listOf(
            TrackerColumn("date", "Date", TrackerColumnType.DATE),
            TrackerColumn("nights", "Nights", TrackerColumnType.NUMBER)
        ),
        "trip",
        "trips"
    )

    /** A report long enough for several pages. */
    private val report = BadgeReport(
        profile = Profile("Alex Scout", "123"),
        badgeName = "Camping",
        requirementsVersion = LocalDate.of(2026, 1, 1),
        completion = Completion(LocalDate.of(2026, 6, 3)),
        counselor = Counselor("Pat Lee", "555-0100", "pat@example.com"),
        requirements = (1..40).map {
            ReportRequirement(
                number = "$it",
                summary = "Requirement number $it of the badge.",
                requiredCount = null,
                completion = Completion(LocalDate.of(2026, 4, 1)),
                notNeeded = false,
                comment = "A comment on requirement $it, ".repeat(it % 5 + 1),
                tracker = ReportTracker(
                    log,
                    listOf(ReportTrackerRow(1, listOf(log.columns[1] to "$it")))
                ),
                children = emptyList()
            )
        },
        createdDate = LocalDate.of(2026, 9, 30)
    )

    private val pages = layOutReport(report, context.resources)

    @After
    fun deleteFile() {
        file.delete()
    }

    private fun writePdf() {
        file.outputStream().use { PdfDocumentWriter().write(pages, it) }
    }

    private fun <T> read(block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }

    @Test
    fun write_makesAPdf_withAPageForEachPage_onLetterPaper() {
        writePdf()

        assertEquals("%PDF-", file.readBytes().copyOf(5).decodeToString())
        read { renderer ->
            assertTrue("Expected several pages, got ${pages.size}", pages.size > 1)
            assertEquals(pages.size, renderer.pageCount)
            repeat(renderer.pageCount) { index ->
                renderer.openPage(index).use {
                    assertEquals(PAGE_WIDTH, it.width)
                    assertEquals(PAGE_HEIGHT, it.height)
                }
            }
        }
    }

    // PdfRenderer can read a page's text from Android 15 on.
    @SdkSuppress(minSdkVersion = 35)
    @Test
    fun write_putsEachPagesText_onThatPageOnly() {
        writePdf()

        val written = read { renderer -> List(renderer.pageCount) { renderer.text(it) } }

        pages.forEachIndexed { index, page ->
            // Spaces can differ in a PDF's text, so they're left out.
            var from = 0
            page.lines.map { it.withoutSpaces() }.forEach { line ->
                val at = written[index].indexOf(line, from)
                assertTrue("Page ${index + 1} is missing \"$line\" after $from", at >= 0)
                from = at + line.length
            }
        }
        // Each requirement is on one page, once.
        (1..40).forEach { number ->
            val title = "$number. Requirement number $number of the badge.".withoutSpaces()
            val found = written.sumOf { Regex(Regex.escape(title)).findAll(it).count() }
            assertEquals("\"$title\" in the PDF", 1, found)
        }
    }

    @RequiresApi(35)
    private fun PdfRenderer.text(index: Int): String = openPage(index).use { page ->
        page.textContents.joinToString("") { it.text }.withoutSpaces()
    }

    private fun String.withoutSpaces() = filterNot { it.isWhitespace() }
}
