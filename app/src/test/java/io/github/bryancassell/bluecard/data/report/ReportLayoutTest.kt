package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.LocaleList
import androidx.core.text.BidiFormatter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Lays reports out with Robolectric's native graphics, which measure and break text with real
 * fonts, as a phone does. PdfDocument itself only runs on a device (PdfDocumentWriterTest).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val date = TrackerColumn("date", "Date", TrackerColumnType.DATE)
    private val nights = TrackerColumn("nights", "Nights", TrackerColumnType.NUMBER)
    private val place = TrackerColumn("place", "Place", TrackerColumnType.TEXT)
    private val log = TrackerDefinition(listOf(date, nights, place), "trip", "trips")
    private val weeks = TrackerDefinition(listOf(place), "week", "weeks", rowCount = 3)

    private fun requirement(
        number: String,
        summary: String,
        completion: Completion? = null,
        comment: String? = null,
        requiredCount: Int? = null,
        tracker: ReportTracker? = null,
        children: List<ReportRequirement> = emptyList()
    ) = ReportRequirement(number, summary, requiredCount, completion, comment, tracker, children)

    private fun report(
        requirements: List<ReportRequirement>,
        profile: Profile = Profile("Alex Scout", "123"),
        counselor: Counselor? = Counselor("Pat Lee", "555-0100", "pat@example.com")
    ) = BadgeReport(
        profile = profile,
        badgeName = "Camping",
        requirementsVersion = LocalDate.of(2026, 1, 1),
        completion = Completion(LocalDate.of(2026, 6, 3)),
        counselor = counselor,
        requirements = requirements,
        createdDate = LocalDate.of(2026, 9, 30)
    )

    private val camping = report(
        listOf(
            requirement(
                "1",
                "Plan a campout.",
                Completion(LocalDate.of(2026, 4, 1)),
                comment = "Planned with my patrol."
            ),
            requirement(
                "2",
                "Do two of these.",
                Completion(null),
                requiredCount = 2,
                children = listOf(
                    requirement("2a", "Cook a meal.", Completion(LocalDate.of(2026, 4, 2))),
                    requirement("2b", "Lead a hike."),
                    requirement("2c", "Pitch a tent.", Completion(null))
                )
            ),
            requirement(
                "3",
                "Keep a camping log.",
                Completion(LocalDate.of(2026, 6, 3)),
                tracker = ReportTracker(
                    log,
                    listOf(
                        ReportTrackerRow(1, listOf(nights to "2", place to "Bear Mountain")),
                        ReportTrackerRow(2, listOf(date to "2026-05-02", place to "Lake Sebago"))
                    )
                )
            ),
            requirement(
                "4",
                "Camp three weeks.",
                tracker = ReportTracker(weeks, listOf(ReportTrackerRow(2, emptyList())))
            )
        )
    )

    private fun layOut(report: BadgeReport, resources: Resources = context.resources) =
        layOutReport(report, resources)

    /** Resources in [locales], BCP 47 tags in order, as `stringsLanguageResources` gives. */
    private fun resources(locales: String): Resources = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(locales))
        }
    ).resources

    @Test
    fun shortReport_isOnePage_withEverythingRecordedInOrder() {
        val pages = layOut(camping)

        assertEquals(1, pages.size)
        assertEquals(
            listOf(
                "Merit badge report",
                "Camping",
                "Scout: Alex Scout",
                "Unit: 123",
                "Completed on Jun 3, 2026",
                "Requirements effective Jan 1, 2026",
                "Created on Sep 30, 2026",
                "Counselor",
                "Name: Pat Lee",
                "Phone: 555-0100",
                "Email: pat@example.com",
                "Requirements",
                "1. Plan a campout.",
                "Completed on Apr 1, 2026",
                "Comment: Planned with my patrol.",
                "2. Do two of these.",
                "Do 2 of 3",
                "Completed",
                "2a. Cook a meal.",
                "Completed on Apr 2, 2026",
                "2b. Lead a hike.",
                "Not completed",
                "2c. Pitch a tent.",
                "Completed",
                "3. Keep a camping log.",
                "Completed on Jun 3, 2026",
                "2 trips",
                "Trip 1",
                "Nights: 2 · Place: Bear Mountain",
                "Trip 2",
                "Date: May 2, 2026 · Place: Lake Sebago",
                "4. Camp three weeks.",
                "Not completed",
                "1 of 3 weeks",
                "Week 2",
                "Page 1 of 1"
            ),
            pages.single().lines
        )
    }

    @Test
    fun noCounselor_leavesOutTheirSection() {
        val lines = layOut(report(emptyList(), counselor = null)).single().lines

        assertFalse("Counselor" in lines)
        assertEquals("Created on Sep 30, 2026", lines[lines.indexOf("Requirements") - 1])
    }

    @Test
    fun counselor_hasOnlyTheDetailsEntered() {
        val lines = layOut(report(emptyList(), counselor = Counselor(phone = "555-0100")))
            .single().lines

        assertEquals(listOf("Counselor", "Phone: 555-0100", "Requirements"), lines.subList(7, 10))
    }

    @Test
    fun trackerValueThatIsntADate_isShownAsStored() {
        val tracker = ReportTracker(log, listOf(ReportTrackerRow(1, listOf(date to "Last May"))))
        val lines = layOut(report(listOf(requirement("1", "Log.", tracker = tracker))))
            .single().lines

        assertTrue("Date: Last May" in lines)
    }

    // A Persian name keeps its own direction, so its period stays at its end
    // (ARCHITECTURE.md, UI layer).
    @Test
    fun textTheScoutTyped_keepsItsOwnDirection() {
        val name = "علی رضایی."
        val lines = layOut(report(emptyList(), profile = Profile(name, "۱۲۳"))).single().lines

        val wrapped = BidiFormatter.getInstance(Locale.US).unicodeWrap(name)
        assertTrue(wrapped != name)
        assertTrue("Scout: $wrapped" in lines)
    }

    // A device in the strings' language keeps its choice of digits (stringsLocales).
    @Test
    fun datesAndNumbers_useTheResourcesLocale() {
        val lines = layOut(camping, resources("en-US-u-nu-arab")).single().lines

        assertTrue("Created on Sep ٣٠, ٢٠٢٦" in lines)
        assertTrue("Do ٢ of ٣" in lines)
        assertEquals("Page ١ of ١", lines.last())
    }

    /**
     * A report with [count] requirements, each with a comment of one to a few lines. Their
     * lengths depend on [count] too, so each count breaks its pages in other places.
     */
    private fun longReport(count: Int) = report(
        (1..count).map {
            requirement(
                "$it",
                "Requirement number $it of the badge.",
                Completion(LocalDate.of(2026, 4, 1)),
                comment = "A comment on requirement $it, ".repeat((it + count) % 7 + 1)
            )
        }
    )

    @Test
    fun longReport_continuesOnMorePages_eachNumbered() {
        val pages = layOut(longReport(60))

        assertTrue("Expected several pages, got ${pages.size}", pages.size > 2)
        pages.forEachIndexed { index, page ->
            assertEquals("Page ${index + 1} of ${pages.size}", page.lines.last())
        }
        // Every requirement is there once, in order.
        val titles = pages.flatMap { it.lines }.filter { it.endsWith("of the badge.") }
        assertEquals((1..60).map { "$it. Requirement number $it of the badge." }, titles)
    }

    // Catches a title left at the foot of a page, away from what it's about, wherever the page
    // breaks fall.
    @Test
    fun requirementTitle_isNeverLastOnAPage() {
        for (count in 20..60) {
            val pages = layOut(longReport(count))
            pages.dropLast(1).forEachIndexed { index, page ->
                val last = page.lines.dropLast(1).last()
                assertFalse(
                    "Page ${index + 1} of $count requirements ends with \"$last\"",
                    last.endsWith("of the badge.")
                )
            }
        }
    }

    /**
     * A report whose "Requirements" heading is further down the first page the longer [nameWords]
     * is, and whose [count] requirements each have a log with a few rows.
     */
    private fun reportWithTrackers(nameWords: Int, count: Int) = report(
        profile = Profile("Alex" + " Scout".repeat(nameWords), "123"),
        requirements = (1..count).map { number ->
            requirement(
                "$number",
                "Requirement number $number of the badge.",
                Completion(LocalDate.of(2026, 4, 1)),
                tracker = ReportTracker(
                    log,
                    (1..(number + nameWords) % 4).map {
                        ReportTrackerRow(it, listOf(nights to "$it", place to "Lake $it"))
                    }
                )
            )
        }
    )

    // A heading followed by a requirement's title, or a tracker's count followed by its first
    // row's title, is a run of paragraphs that each go with the next. Catches one left at the
    // foot of a page wherever the page breaks fall. A count with no rows after it ends its
    // requirement, so it can be.
    @Test
    fun headingsTitlesAndTrackerCounts_areNeverLastOnAPage() {
        val kept = Regex("""Counselor|Requirements|.* of the badge\.|[1-9]\d* trips?|Trip \d+""")
        for (nameWords in 0..240 step 3) {
            val pages = layOut(reportWithTrackers(nameWords, count = 12))
            pages.dropLast(1).forEachIndexed { index, page ->
                val last = page.lines.dropLast(1).last()
                assertFalse(
                    "Page ${index + 1}, $nameWords words in the name, ends with \"$last\"",
                    kept.matches(last)
                )
            }
        }
    }

    // A Persian comment keeps its direction on the next page (ARCHITECTURE.md, UI layer).
    @Test
    fun typedTextSplitAcrossPages_keepsItsDirectionOnTheNextPage() {
        val comment = "سلام دنیا ".repeat(800).trim()
        val pages = layOut(report(listOf(requirement("1", "Write.", comment = comment))))

        val rightToLeftEmbedding = "\u202B"
        val continued = pages[1].lines.first()
        assertTrue("Page 2 starts \"$continued\"", continued.startsWith(rightToLeftEmbedding))
    }

    @Test
    fun openDirections_areThoseNotClosed_inOrder() {
        // Right-to-left embedding, left-to-right isolate, and their closings.
        val rle = "\u202B"
        val pdf = "\u202C"
        val lri = "\u2066"
        val pdi = "\u2069"

        assertEquals("", openDirections("a$rle b$pdf c"))
        assertEquals(rle, openDirections("a$rle b"))
        assertEquals(rle + lri, openDirections("$rle a$lri b"))
        // A PDF doesn't close an embedding outside the isolate it's in.
        assertEquals(rle + lri, openDirections("$rle a$lri b$pdf"))
        // A PDI closes its isolate and what's open inside it.
        assertEquals(rle, openDirections("$rle a$lri b$rle c$pdi"))
        // A line break ends the paragraph, and everything open in it.
        assertEquals("", openDirections("$rle a\nb"))
    }

    @Test
    fun paragraphLongerThanThePageSpace_continuesOnTheNextPage_withEveryWordOnce() {
        val words = (1..1200).map { "word$it" }
        val pages = layOut(
            report(listOf(requirement("1", "Write.", comment = words.joinToString(" "))))
        )

        assertTrue(pages.size > 1)
        val text = pages.flatMap { it.lines.dropLast(1) }.joinToString(" ")
        val written = Regex("word\\d+").findAll(text).map { it.value }.toList()
        assertEquals(words, written)
    }

    @Test
    fun draw_drawsThePagesLines_insideTheMargins() {
        val pages = layOut(longReport(40))

        pages.forEach { page ->
            val canvas = RecordingCanvas()
            page.draw(canvas)

            assertEquals(page.lines, canvas.drawn.map { it.text.trim() })
            val (content, footer) = canvas.drawn.partition { it.y < PAGE_HEIGHT - 54 }
            assertEquals(1, footer.size)
            assertTrue(footer.single().y < PAGE_HEIGHT)
            content.forEach {
                assertTrue("$it", it.x >= 54 && it.y > 54)
            }
        }
    }

    @Test
    fun subRequirements_areIndented() {
        val canvas = RecordingCanvas()
        layOut(camping).single().draw(canvas)

        val two = canvas.drawn.first { it.text == "2. Do two of these." }
        val twoA = canvas.drawn.first { it.text == "2a. Cook a meal." }
        assertEquals(54f, two.x)
        assertEquals(72f, twoA.x)
    }

    @Test
    fun whatFollowsAHeading_isTheSameDistanceUnderIt() {
        val canvas = RecordingCanvas()
        layOut(camping).single().draw(canvas)

        fun gapUnder(heading: String, next: String): Float {
            val drawn = canvas.drawn.map { it.text }
            return canvas.drawn[drawn.indexOf(next)].y - canvas.drawn[drawn.indexOf(heading)].y
        }
        assertEquals(
            gapUnder("Counselor", "Name: Pat Lee"),
            gapUnder("Requirements", "1. Plan a campout.")
        )
    }

    // If the app is translated into a right-to-left language, its report reads from the right.
    @Test
    fun rightToLeftLocale_laysTextOutFromTheRight() {
        val canvas = RecordingCanvas()
        layOut(camping, resources("fa-IR")).single().draw(canvas)

        val kind = canvas.drawn.first { it.text == "Merit badge report" }
        assertTrue("Starts at ${kind.x}", kind.x > PAGE_WIDTH / 2)
    }

    /** Text drawn on a canvas, starting at ([x], [y]) on the page, its baseline. */
    private data class DrawnText(val text: String, val x: Float, val y: Float)

    /** A page-sized canvas that records the text drawn on it. */
    private class RecordingCanvas :
        Canvas(Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)) {
        val drawn = mutableListOf<DrawnText>()

        override fun drawText(
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            y: Float,
            paint: Paint
        ) {
            record(text.subSequence(start, end), x, y)
            super.drawText(text, start, end, x, y, paint)
        }

        override fun drawTextRun(
            text: CharSequence,
            start: Int,
            end: Int,
            contextStart: Int,
            contextEnd: Int,
            x: Float,
            y: Float,
            isRtl: Boolean,
            paint: Paint
        ) {
            record(text.subSequence(start, end), x, y)
            super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
        }

        private fun record(text: CharSequence, x: Float, y: Float) {
            val values = FloatArray(9).also { Matrix(matrix).getValues(it) }
            drawn += DrawnText(
                text.toString(),
                values[Matrix.MTRANS_X] + x,
                values[Matrix.MTRANS_Y] + y
            )
        }
    }
}
