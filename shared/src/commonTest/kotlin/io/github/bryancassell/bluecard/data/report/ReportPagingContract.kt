package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.progress.Completion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * How a report is placed on pages, with each platform's line heights and breaks. Runs with
 * Android's typesetter (ReportLayoutTest, which checks the rest of the layout too) and iOS's
 * (CoreTextPagingTest).
 */
abstract class ReportPagingContract<T : ReportText> {
    /** A typesetter in US English. */
    protected abstract val typesetter: ReportTypesetter<T>

    private fun requirement(number: Int, summary: String, comment: String) = ReportRequirement(
        number = "$number",
        summary = summary,
        requiredCount = null,
        completion = Completion(LocalDate(2026, 4, 1)),
        notNeeded = false,
        notRecorded = false,
        signedOffBy = null,
        comment = comment,
        tracker = null,
        meritBadges = null,
        children = emptyList()
    )

    private fun report(requirements: List<ReportRequirement>) = AdvancementReport(
        profile = Profile("Alex Scout", "123"),
        kind = ReportKind.MeritBadge,
        name = "Camping",
        requirementsVersion = LocalDate(2026, 1, 1),
        completion = Completion(LocalDate(2026, 6, 3)),
        earnedWith = null,
        counselor = null,
        requirements = requirements,
        createdDate = LocalDate(2026, 9, 30)
    )

    /**
     * A report with [count] requirements, each with a comment of one to a few lines. Their
     * lengths depend on [count] too, so each count breaks its pages in other places.
     */
    protected fun longReport(count: Int) = report(
        (1..count).map {
            requirement(
                it,
                "Requirement number $it of the badge.",
                "A comment on requirement $it, ".repeat((it + count) % 7 + 1)
            )
        }
    )

    private suspend fun layOut(report: AdvancementReport) =
        layOutReport(report, getSystemResourceEnvironment(), typesetter)

    @Test
    fun longReport_continuesOnMorePages_eachNumbered() = runTest {
        val pages = layOut(longReport(60))

        assertTrue(pages.size > 2, "Expected several pages, got ${pages.size}")
        pages.forEachIndexed { index, page ->
            assertEquals("Page ${index + 1} of ${pages.size}", page.lines.last())
        }
        // Every requirement is there once, in order.
        val titles = pages.flatMap { it.lines }.filter { it.endsWith("of the badge.") }
        assertEquals((1..60).map { "$it. Requirement number $it of the badge." }, titles)
    }

    @Test
    fun requirementTitle_isNeverLastOnAPage() = runTest {
        for (count in 20..60) {
            val pages = layOut(longReport(count))
            pages.dropLast(1).forEachIndexed { index, page ->
                val last = page.lines.dropLast(1).last()
                assertFalse(
                    last.endsWith("of the badge."),
                    "Page ${index + 1} of $count requirements ends with \"$last\""
                )
            }
        }
    }

    @Test
    fun paragraphLongerThanThePageSpace_continuesOnTheNextPage_withEveryWordOnce() = runTest {
        val words = (1..1200).map { "word$it" }
        val pages = layOut(report(listOf(requirement(1, "Write.", words.joinToString(" ")))))

        assertTrue(pages.size > 1)
        val text = pages.flatMap { it.lines.dropLast(1) }.joinToString(" ")
        val written = Regex("word\\d+").findAll(text).map { it.value }.toList()
        assertEquals(words, written)
    }

    // StaticLayout gives text that ends in a line break an empty line after it, which the part
    // of a note before a page break would end with.
    @Test
    fun noteSplitAtOneOfItsLineBreaks_endsItsPage_insideTheMargins() = runTest {
        val note = (1..120).joinToString("\n") { "Night $it at the lake." }
        val pages = layOut(report(listOf(requirement(1, "Camp.", note))))

        assertTrue(pages.size > 1)
        pages.forEach { page ->
            page.texts.dropLast(1).forEach { placed ->
                assertTrue(
                    placed.y + placed.text.height <= PAGE_HEIGHT - 54,
                    "A block ends at y ${placed.y + placed.text.height}"
                )
            }
            assertFalse("" in page.lines, "A page has an empty line")
        }
        val nights = pages.flatMap { it.lines }.filter { "at the lake." in it }
        assertEquals(
            (1..120).map { "Night $it at the lake." },
            nights.map { it.removePrefix("Notes: ") }
        )
    }

    @Test
    fun everyBlock_isInsideTheMargins() = runTest {
        layOut(longReport(60)).forEach { page ->
            page.texts.dropLast(1).forEach { placed ->
                assertTrue(placed.y >= 54, "A block starts at y ${placed.y}")
                assertTrue(
                    placed.y + placed.text.height <= PAGE_HEIGHT - 54,
                    "A block ends at y ${placed.y + placed.text.height}"
                )
            }
        }
    }
}
