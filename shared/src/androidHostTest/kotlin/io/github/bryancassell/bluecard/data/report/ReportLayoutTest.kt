package io.github.bryancassell.bluecard.data.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.text.BidiFormatter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.catalog.ColumnTotal
import io.github.bryancassell.bluecard.data.catalog.MeritBadge
import io.github.bryancassell.bluecard.data.catalog.MeritBadgesNeeded
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.catalog.TrackerDefinition
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.data.progress.EarnedBadge
import io.github.bryancassell.bluecard.data.progress.MeritBadgeCredit
import io.github.bryancassell.bluecard.data.progress.TrackerTotal
import io.github.bryancassell.bluecard.text.AndroidLocaleFormats
import java.math.BigDecimal
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getSystemResourceEnvironment
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
class ReportLayoutTest : ReportPagingContract<StaticLayoutText>() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    override val typesetter = StaticLayoutTypesetter(Locale.US)

    private val date = TrackerColumn("date", "Date", TrackerColumnType.DATE)
    private val nights = TrackerColumn("nights", "Nights", TrackerColumnType.NUMBER)
    private val place = TrackerColumn("place", "Place", TrackerColumnType.TEXT)
    private val log = TrackerDefinition(listOf(date, nights, place), "trip", "trips")
    private val weeks = TrackerDefinition(listOf(place), "week", "weeks", rowCount = 3)
    private val twentyNights = ColumnTotal(20, "night", "nights")

    private fun requirement(
        number: String,
        summary: String,
        completion: Completion? = null,
        comment: String? = null,
        signedOffBy: String? = null,
        requiredCount: Int? = null,
        notNeeded: Boolean = false,
        notRecorded: Boolean = false,
        tracker: ReportTracker? = null,
        meritBadges: ReportMeritBadges? = null,
        children: List<ReportRequirement> = emptyList()
    ) = ReportRequirement(
        number = number,
        summary = summary,
        requiredCount = requiredCount,
        completion = completion,
        notNeeded = notNeeded,
        notRecorded = notRecorded,
        signedOffBy = signedOffBy,
        comment = comment,
        tracker = tracker,
        meritBadges = meritBadges,
        children = children
    )

    private fun report(
        requirements: List<ReportRequirement>,
        profile: Profile = Profile("Alex Scout", "123"),
        counselor: Counselor? = Counselor("Pat Lee", "555-0100", "pat@example.com")
    ) = AdvancementReport(
        profile = profile,
        kind = ReportKind.MeritBadge,
        name = "Camping",
        requirementsVersion = LocalDate(2026, 1, 1),
        completion = Completion(LocalDate(2026, 6, 3)),
        earnedWith = null,
        counselor = counselor,
        requirements = requirements,
        createdDate = LocalDate(2026, 9, 30)
    )

    /** A rank's report, earned on Jun 3, 2026 unless [completion] says otherwise. */
    private fun rankReport(
        requirements: List<ReportRequirement> = emptyList(),
        completion: Completion? = Completion(LocalDate(2026, 6, 3)),
        earnedWith: String? = null
    ) = AdvancementReport(
        profile = Profile("Alex Scout", "123"),
        kind = ReportKind.Rank,
        name = "Star",
        requirementsVersion = LocalDate(2026, 1, 1),
        completion = completion,
        earnedWith = earnedWith,
        counselor = null,
        requirements = requirements,
        createdDate = LocalDate(2026, 9, 30)
    )

    private val camping = report(
        listOf(
            requirement(
                "1",
                "Plan a campout.",
                Completion(LocalDate(2026, 4, 1)),
                comment = "Planned with my patrol."
            ),
            requirement(
                "2",
                "Do two of these.",
                Completion(null),
                requiredCount = 2,
                children = listOf(
                    requirement("2a", "Cook a meal.", Completion(LocalDate(2026, 4, 2))),
                    requirement("2b", "Lead a hike.", notNeeded = true),
                    requirement("2c", "Pitch a tent.", Completion(null))
                )
            ),
            requirement(
                "3",
                "Keep a camping log.",
                Completion(LocalDate(2026, 6, 3)),
                tracker = ReportTracker(
                    log,
                    listOf(
                        ReportTrackerRow(1, listOf(nights to "2", place to "Bear Mountain")),
                        ReportTrackerRow(2, listOf(date to "2026-05-02", place to "Lake Sebago"))
                    ),
                    listOf(TrackerTotal(BigDecimal("2"), twentyNights))
                )
            ),
            requirement(
                "4",
                "Camp three weeks.",
                tracker = ReportTracker(weeks, listOf(ReportTrackerRow(2, emptyList())))
            )
        )
    )

    private fun layOut(
        report: AdvancementReport,
        locale: Locale = context.resources.configuration.locales[0]
    ) = runBlocking {
        layOutReport(report, getSystemResourceEnvironment(), StaticLayoutTypesetter(locale))
    }

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
                "Notes: Planned with my patrol.",
                "2. Do two of these.",
                "Do 2 of 3",
                "Completed",
                "2a. Cook a meal.",
                "Completed on Apr 2, 2026",
                "2b. Lead a hike.",
                "Not needed",
                "2c. Pitch a tent.",
                "Completed",
                "3. Keep a camping log.",
                "Completed on Jun 3, 2026",
                "2 trips",
                "2 of 20 nights",
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
    fun rankReport_isTitledAsARank_saysWhenItWasEarned_andHasNoCounselor() {
        val lines = layOut(rankReport()).single().lines

        assertEquals(
            listOf(
                "Rank report",
                "Star",
                "Scout: Alex Scout",
                "Unit: 123",
                "Earned on Jun 3, 2026",
                "Requirements effective Jan 1, 2026",
                "Created on Sep 30, 2026",
                "Requirements",
                "Page 1 of 1"
            ),
            lines
        )
    }

    @Test
    fun rankReport_saysWhetherTheRankIsEarned_asItsPageDoes() {
        fun status(report: AdvancementReport) = layOut(report).single().lines[4]

        assertEquals("Earned", status(rankReport(completion = Completion(null))))
        assertEquals(
            "Counted as earned with Life",
            status(rankReport(completion = null, earnedWith = "Life"))
        )
    }

    @Test
    fun ranksRequirement_saysWhoSignedOffOnIt_aboveItsNotes() {
        val lines = layOut(
            rankReport(
                listOf(
                    requirement(
                        "1",
                        "Earn Scout.",
                        Completion(LocalDate(2026, 4, 1)),
                        comment = "At the troop meeting.",
                        signedOffBy = "Mr. Rivera"
                    )
                )
            )
        ).single().lines

        val title = lines.indexOf("1. Earn Scout.")
        assertEquals(
            listOf(
                "Completed on Apr 1, 2026",
                "Signed off by: Mr. Rivera",
                "Notes: At the troop meeting."
            ),
            lines.subList(title + 1, title + 4)
        )
    }

    private val eagleRequiredBadge = MeritBadge(
        id = "camping",
        name = "Camping",
        summary = "Our summary of Camping.",
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        eagleRequired = true,
        requirementVersions = emptyList()
    )
    private val electiveBadge = eagleRequiredBadge.copy(
        id = "chess",
        name = "Chess",
        eagleRequired = false
    )

    @Test
    fun requirementThatAsksForMeritBadges_hasTheCounts_thenEachBadgeCompleted() {
        val credit = MeritBadgeCredit(
            needed = MeritBadgesNeeded(total = 6, eagleRequired = 4),
            completed = 2,
            eagleRequired = 1,
            completion = null
        )
        val badges = listOf(
            EarnedBadge(eagleRequiredBadge, LocalDate(2026, 5, 3), true),
            EarnedBadge(electiveBadge, null, false)
        )
        val lines = layOut(
            rankReport(
                listOf(
                    requirement(
                        "3",
                        "Earn six merit badges.",
                        comment = "Two to go.",
                        meritBadges = ReportMeritBadges(credit, badges)
                    )
                )
            )
        ).single().lines

        val title = lines.indexOf("3. Earn six merit badges.")
        assertEquals(
            listOf(
                "Not completed",
                "Notes: Two to go.",
                "2 of 6 merit badges",
                "1 of 4 Eagle-required",
                "Camping · Completed on May 3, 2026 · Eagle-required",
                "Chess · Completed"
            ),
            lines.subList(title + 1, title + 7)
        )
    }

    // As on its page, a badge counts as Eagle-required by the requirement's rule.
    @Test
    fun meritBadge_isEagleRequired_onlyIfItCountsAsEagleRequiredTowardTheRequirement() {
        val credit = MeritBadgeCredit(
            needed = MeritBadgesNeeded(total = 21, eagleRequired = 13, eagleGroupsCountOnce = true),
            completed = 1,
            eagleRequired = 0,
            completion = null
        )
        // Eagle-required, but not the first of its group completed.
        val badges = listOf(EarnedBadge(eagleRequiredBadge, LocalDate(2026, 5, 3), false))
        val lines = layOut(
            rankReport(
                listOf(
                    requirement("3", "Earn 21.", meritBadges = ReportMeritBadges(credit, badges))
                )
            )
        ).single().lines

        assertTrue("Camping · Completed on May 3, 2026" in lines)
    }

    @Test
    fun requirementThatAsksForMeritBadges_withNone_hasOnlyTheCounts() {
        val credit = MeritBadgeCredit(
            needed = MeritBadgesNeeded(total = 1, eagleRequired = 1),
            completed = 0,
            eagleRequired = 0,
            completion = null
        )
        val lines = layOut(
            rankReport(
                listOf(
                    requirement(
                        "3",
                        "Earn one.",
                        meritBadges = ReportMeritBadges(credit, emptyList())
                    )
                )
            )
        ).single().lines

        val title = lines.indexOf("3. Earn one.")
        assertEquals(
            listOf("Not completed", "0 of 1 merit badge", "0 of 1 Eagle-required", "Page 1 of 1"),
            lines.subList(title + 1, lines.size)
        )
    }

    @Test
    fun requirementNotRecorded_saysSo() {
        val lines = layOut(report(listOf(requirement("1", "Plan.", notRecorded = true))))
            .single().lines

        assertEquals("Not recorded", lines[lines.indexOf("1. Plan.") + 1])
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
    fun counselorsPhone_isShownFormatted() {
        val report = report(emptyList(), counselor = Counselor(phone = "5551234567"))
            .copy(counselorPhone = "(555) 123-4567")

        assertEquals("Phone: (555) 123-4567", layOut(report).single().lines[8])
    }

    @Test
    fun trackerValueThatIsntADate_isShownAsStored() {
        val tracker = ReportTracker(log, listOf(ReportTrackerRow(1, listOf(date to "Last May"))))
        val lines = layOut(report(listOf(requirement("1", "Log.", tracker = tracker))))
            .single().lines

        assertTrue("Date: Last May" in lines)
    }

    @Test
    fun multilineTrackerValue_keepsItsLineBreaks_andTheNextValueStartsANewLine() {
        val details = TrackerColumn("details", "Details", TrackerColumnType.MULTILINE_TEXT)
        val values = listOf(nights to "2", details to "Pitched a tent\nCooked", place to "Lake")
        val tracker = ReportTracker(
            TrackerDefinition(listOf(nights, details, place), "trip", "trips"),
            listOf(ReportTrackerRow(1, values))
        )
        val lines = layOut(report(listOf(requirement("1", "Log.", tracker = tracker))))
            .single().lines

        val row = lines.indexOf("Nights: 2 · Details: Pitched a tent")
        assertEquals(listOf("Cooked", "Place: Lake"), lines.subList(row + 1, row + 3))
    }

    // As on the requirement's page, a log that needs a number of rows is counted out of them.
    @Test
    fun logThatNeedsRows_isCountedOutOfThem() {
        val rows = (1..3).map { ReportTrackerRow(it, listOf(place to "Lake")) }
        val tracker = ReportTracker(log.copy(rowsNeeded = 2), rows)
        val lines = layOut(report(listOf(requirement("1", "Log.", tracker = tracker))))
            .single().lines

        assertEquals("Trip 1", lines[lines.indexOf("3 of 2 trips") + 1])
    }

    // A line break ends the wrapping that keeps typed text's direction, so each line has its own.
    @Test
    fun typedTextOnSeveralLines_keepsItsDirectionOnEachLine() {
        val first = "چادر زدم."
        val second = "شام پختم."
        val lines = layOut(
            report(listOf(requirement("1", "Camp.", comment = "$first\n$second")))
        ).single().lines

        fun wrap(text: String) = BidiFormatter.getInstance(Locale.US).unicodeWrap(text)
        assertTrue(wrap(second) != second)
        assertTrue("Notes: ${wrap(first)}" in lines)
        assertTrue(wrap(second) in lines)
    }

    // A Persian name keeps its own direction, so its period stays at its end
    // (ARCHITECTURE.md, Language and layout direction).
    // A paragraph separator ends a paragraph for the bidi algorithm, as a line break does.
    @Test
    fun typedTextWithAParagraphSeparator_keepsItsDirectionInEachParagraph() {
        val first = "چادر زدم."
        val second = "شام پختم."
        val lines = layOut(
            report(listOf(requirement("1", "Camp.", comment = "$first\u2029$second")))
        ).single().lines

        fun wrap(text: String) = BidiFormatter.getInstance(Locale.US).unicodeWrap(text)
        val notes = lines.single { it.startsWith("Notes: ") }
        assertTrue(notes, wrap(first) in notes)
        assertTrue(notes, wrap(second) in lines.joinToString("\n"))
    }

    @Test
    fun textTheScoutTyped_keepsItsOwnDirection() {
        val name = "علی رضایی."
        val lines = layOut(report(emptyList(), profile = Profile(name, "۱۲۳"))).single().lines

        val wrapped = BidiFormatter.getInstance(Locale.US).unicodeWrap(name)
        assertTrue(wrapped != name)
        assertTrue("Scout: $wrapped" in lines)
    }

    // A device in the strings' language keeps its region (stringsLocales).
    @Test
    fun dates_useTheGivenLocale() {
        val lines = layOut(camping, Locale.forLanguageTag("en-GB")).single().lines

        assertTrue(lines.toString(), "Created on 30 Sept 2026" in lines)
    }

    /**
     * A report whose "Requirements" heading is further down the first page the longer [nameWords]
     * is, and whose [count] requirements each have a log with a few rows, and their nights added
     * up if [withTotals].
     */
    private fun reportWithTrackers(nameWords: Int, count: Int, withTotals: Boolean) = report(
        profile = Profile("Alex" + " Scout".repeat(nameWords), "123"),
        requirements = (1..count).map { number ->
            requirement(
                "$number",
                "Requirement number $number of the badge.",
                Completion(LocalDate(2026, 4, 1)),
                tracker = (1..(number + nameWords) % 4).let { rows ->
                    ReportTracker(
                        log,
                        rows.map {
                            ReportTrackerRow(it, listOf(nights to "$it", place to "Lake $it"))
                        },
                        if (withTotals) {
                            listOf(TrackerTotal(rows.sum().toBigDecimal(), twentyNights))
                        } else {
                            emptyList()
                        }
                    )
                }
            )
        }
    )

    // A heading followed by a requirement's title, or a tracker's count followed by its totals
    // and its first row's title, is a run of paragraphs that each go with the next. Catches one
    // left at the foot of a page wherever the page breaks fall. A count or total with nothing
    // after it ends its requirement, so it can be.
    @Test
    fun headingsTitlesAndTrackerCounts_areNeverLastOnAPage() {
        val always = """Counselor|Requirements|.* of the badge\.|Trip \d+"""
        // With totals after it, a count with no rows isn't last in its requirement.
        val kept = mapOf(
            false to Regex("""$always|[1-9]\d* trips?"""),
            true to Regex("""$always|\d+ trips?|[1-9]\d* of 20 nights""")
        )
        for ((withTotals, keptLines) in kept) {
            for (nameWords in 0..240 step 3) {
                val pages = layOut(reportWithTrackers(nameWords, count = 12, withTotals))
                pages.dropLast(1).forEachIndexed { index, page ->
                    val last = page.lines.dropLast(1).last()
                    assertFalse(
                        "Page ${index + 1}, $nameWords words in the name, totals $withTotals, " +
                            "ends with \"$last\"",
                        keptLines.matches(last)
                    )
                }
            }
        }
    }

    // A Persian comment keeps its direction on the next page (see ReportLayout).
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
        // So do the other paragraph separators, such as one pasted into notes.
        assertEquals("", openDirections("$rle a\u2029b"))
        assertEquals("", openDirections("$rle a\u0085b"))
        // A line separator only breaks the line.
        assertEquals(rle, openDirections("$rle a\u2028b"))
    }

    // As a typesetter whose every line is taller than a page might. The time limit stops a run
    // that adds pages without end, which would otherwise run until it's out of memory.
    @Test(timeout = 20_000)
    fun lineTallerThanAPage_fails_ratherThanAddingPagesWithoutEnd() {
        class TallText(override val text: String) : ReportText {
            override val lineCount = 1

            override fun lineStart(line: Int) = 0

            override fun lineBottom(line: Int) = PAGE_HEIGHT + 1f
        }
        val tall = object : ReportTypesetter<TallText> {
            override val formats = AndroidLocaleFormats(Locale.US)
            override val rightToLeft = false

            override fun layOut(text: String, style: ReportStyle, width: Int, centered: Boolean) =
                TallText(text)
        }

        val failure = runCatching {
            runBlocking { layOutReport(camping, getSystemResourceEnvironment(), tall) }
        }.exceptionOrNull()

        assertTrue("$failure", failure is IllegalStateException)
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
        layOut(camping, Locale.forLanguageTag("fa-IR")).single().draw(canvas)

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
