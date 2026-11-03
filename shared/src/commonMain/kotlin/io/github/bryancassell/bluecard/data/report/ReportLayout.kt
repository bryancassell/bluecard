package io.github.bryancassell.bluecard.data.report

import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.storedDate
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.report_comment
import io.github.bryancassell.bluecard.resources.report_completed
import io.github.bryancassell.bluecard.resources.report_completed_on
import io.github.bryancassell.bluecard.resources.report_counselor
import io.github.bryancassell.bluecard.resources.report_counselor_email
import io.github.bryancassell.bluecard.resources.report_counselor_name
import io.github.bryancassell.bluecard.resources.report_counselor_phone
import io.github.bryancassell.bluecard.resources.report_created_on
import io.github.bryancassell.bluecard.resources.report_earned
import io.github.bryancassell.bluecard.resources.report_earned_on
import io.github.bryancassell.bluecard.resources.report_earned_with
import io.github.bryancassell.bluecard.resources.report_kind
import io.github.bryancassell.bluecard.resources.report_kind_rank
import io.github.bryancassell.bluecard.resources.report_labeled_value
import io.github.bryancassell.bluecard.resources.report_not_completed
import io.github.bryancassell.bluecard.resources.report_page
import io.github.bryancassell.bluecard.resources.report_requirement
import io.github.bryancassell.bluecard.resources.report_requirements
import io.github.bryancassell.bluecard.resources.report_requirements_version
import io.github.bryancassell.bluecard.resources.report_scout
import io.github.bryancassell.bluecard.resources.report_signed_off_by
import io.github.bryancassell.bluecard.resources.report_unit
import io.github.bryancassell.bluecard.resources.requirement_choice
import io.github.bryancassell.bluecard.resources.requirement_eagle_required_count
import io.github.bryancassell.bluecard.resources.requirement_merit_badge_eagle_required
import io.github.bryancassell.bluecard.resources.requirement_merit_badges_count
import io.github.bryancassell.bluecard.resources.requirement_not_needed
import io.github.bryancassell.bluecard.resources.requirement_not_recorded
import io.github.bryancassell.bluecard.resources.tracker_count
import io.github.bryancassell.bluecard.resources.tracker_count_of
import io.github.bryancassell.bluecard.resources.tracker_row_title
import io.github.bryancassell.bluecard.resources.tracker_total
import io.github.bryancassell.bluecard.resources.tracker_value_separator
import io.github.bryancassell.bluecard.text.LocaleFormats
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

// A badge's or rank's report, laid out on US Letter pages, as Scouting America is in the US.
// Sizes are in PDF points, 1/72 of an inch.

/** The width of a report's pages: 8.5 inches. */
const val PAGE_WIDTH = 612

/** The height of a report's pages: 11 inches. */
const val PAGE_HEIGHT = 792

private const val MARGIN = 54
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN

/** How far each level of sub-requirements is indented. */
private const val INDENT = 18

/** The top of the page number, in the bottom margin. */
private const val FOOTER_TOP = CONTENT_BOTTOM + 18

/** The text styles of a report, in the platform's default font. */
enum class ReportStyle(val size: Float, val bold: Boolean) {
    Title(20f, true),
    Heading(14f, true),
    Subheading(11f, true),
    Body(11f, false),
    Footer(9f, false)
}

/**
 * A block of [text], broken into lines by a platform's [ReportTypesetter]. Positions are in points
 * down from the block's top.
 */
interface ReportText {
    val text: String

    /** How many lines [text] takes: at least one, as empty text takes one empty line. */
    val lineCount: Int

    /** Where line [line] starts in [text]. It ends where the next one starts. */
    fun lineStart(line: Int): Int

    /** The bottom of line [line], which is the top of the line after it. */
    fun lineBottom(line: Int): Float
}

/** How tall the block is: down to the bottom of its last line. */
val ReportText.height: Float get() = lineBottom(lineCount - 1)

/** The block's text, a line at a time, without the spaces and line breaks around each line. */
fun ReportText.lines(): List<String> = List(lineCount) {
    val end = if (it + 1 < lineCount) lineStart(it + 1) else text.length
    text.substring(lineStart(it), end).trim()
}

/**
 * Breaks a report's text into lines with a platform's text engine, in one locale: the strings'
 * (ARCHITECTURE.md, Language and layout direction).
 */
interface ReportTypesetter<T : ReportText> {
    /** Writes dates, totals and text the scout typed in the locale, as the screens do. */
    val formats: LocaleFormats

    /** Whether the locale is written right to left, so text starts at the right margin. */
    val rightToLeft: Boolean

    /**
     * Breaks [text] in [style] into lines up to [width] points wide, each starting where the
     * locale's lines start, or [centered]. A line break in [text] starts a new line.
     *
     * Where each line breaks must depend only on the text before it, without hyphens, so the
     * rest of a paragraph that's split across pages, laid out again on its own, breaks the same
     * way.
     */
    fun layOut(text: String, style: ReportStyle, width: Int, centered: Boolean = false): T
}

/** One page of a report: blocks of text, each at its place on the page. */
class ReportPage<T : ReportText> internal constructor(val texts: List<PlacedText<T>>) {
    /** The page's text, a line at a time, in the order it's laid out. */
    val lines: List<String> get() = texts.flatMap { it.text.lines() }
}

/** A block of [text] with its top left corner at ([x], [y]) on the page. */
class PlacedText<T : ReportText>(val text: T, val x: Float, val y: Float)

/**
 * Lays [report] out on pages, with its strings from [environment], such as
 * `getSystemResourceEnvironment()`, which reads the default locale the app makes the strings'
 * (`useStringsLocalesByDefault`). [typesetter] is in the strings' locale: dates and totals are
 * formatted in it, and text is laid out in its direction (ARCHITECTURE.md, Language and layout
 * direction).
 */
suspend fun <T : ReportText> layOutReport(
    report: AdvancementReport,
    environment: ResourceEnvironment,
    typesetter: ReportTypesetter<T>
): List<ReportPage<T>> {
    val valueSeparator = getString(environment, Res.string.tracker_value_separator)
    return ReportComposer(typesetter, environment, valueSeparator).apply { add(report) }.pages()
}

/** A paragraph of a report, before it's placed on a page. */
private class Paragraph(
    val text: String,
    val style: ReportStyle,
    val indent: Int,
    /** Space above it, unless it starts a page. */
    val spaceBefore: Float,
    /** Whether it goes on the next page with the first line of the paragraph after it. */
    val keepWithNext: Boolean
)

/** Writes a report's paragraphs, then places them on pages. */
private class ReportComposer<T : ReportText>(
    private val typesetter: ReportTypesetter<T>,
    private val environment: ResourceEnvironment,
    // Between a tracker row's values, as on screen.
    private val valueSeparator: String
) {
    // Dates, totals and typed text are written as on screen.
    private val formats = typesetter.formats

    private val paragraphs = mutableListOf<Paragraph>()

    suspend fun add(report: AdvancementReport) {
        val profile = report.profile
        val kind = when (report.kind) {
            ReportKind.MeritBadge -> Res.string.report_kind
            ReportKind.Rank -> Res.string.report_kind_rank
        }
        add(ReportStyle.Body, string(kind))
        add(ReportStyle.Title, report.name, spaceBefore = 2f)
        add(
            ReportStyle.Body,
            string(Res.string.report_scout, typed(profile.name)),
            spaceBefore = 8f
        )
        add(ReportStyle.Body, string(Res.string.report_unit, typed(profile.unitNumber)))
        val status = when (report.kind) {
            ReportKind.MeritBadge -> completionText(report.completion)
            ReportKind.Rank -> earnedText(report)
        }
        add(ReportStyle.Body, status)
        val version = date(report.requirementsVersion)
        add(ReportStyle.Body, string(Res.string.report_requirements_version, version))
        add(ReportStyle.Body, string(Res.string.report_created_on, date(report.createdDate)))
        report.counselor?.let { counselor ->
            addHeading(Res.string.report_counselor)
            counselor.name?.let { addLabeled(Res.string.report_counselor_name, typed(it)) }
            counselor.phone?.let {
                addLabeled(Res.string.report_counselor_phone, typed(report.counselorPhone ?: it))
            }
            counselor.email?.let { addLabeled(Res.string.report_counselor_email, typed(it)) }
        }
        addHeading(Res.string.report_requirements)
        report.requirements.forEach { add(it, depth = 0) }
    }

    private suspend fun add(requirement: ReportRequirement, depth: Int) {
        val indent = depth * INDENT
        add(
            ReportStyle.Subheading,
            string(Res.string.report_requirement, requirement.number, requirement.summary),
            indent,
            spaceBefore = 10f,
            keepWithNext = true
        )
        requirement.requiredCount?.let {
            val choice = string(Res.string.requirement_choice, it, requirement.children.size)
            add(ReportStyle.Body, choice, indent)
        }
        val status = when {
            requirement.notNeeded -> string(Res.string.requirement_not_needed)
            requirement.notRecorded -> string(Res.string.requirement_not_recorded)
            else -> completionText(requirement.completion)
        }
        add(ReportStyle.Body, status, indent)
        requirement.signedOffBy?.let {
            addLabeled(Res.string.report_signed_off_by, typed(it), indent)
        }
        requirement.comment?.let { addLabeled(Res.string.report_comment, typed(it), indent) }
        requirement.tracker?.let { add(it, indent) }
        requirement.meritBadges?.let { add(it, indent) }
        requirement.children.forEach { add(it, depth + 1) }
    }

    private suspend fun add(tracker: ReportTracker, indent: Int) {
        val definition = tracker.definition
        val recorded = tracker.rows.size
        // As on the requirement's page: "5 sessions" in a log, or "8 of 12 weeks" or
        // "6 of 10 animals" when it's out of a number.
        val rows = definition.rowsLabel(recorded)
        val count = definition.rowsOutOf?.let {
            string(Res.string.tracker_count_of, recorded, it, rows)
        } ?: string(Res.string.tracker_count, recorded, rows)
        // Then its totals, such as "4 of 6 hours", as on the requirement's page.
        val totals = tracker.totals.map { (sum, total) ->
            string(
                Res.string.tracker_total,
                formats.formatTotal(sum),
                total.needed,
                total.neededLabel
            )
        }
        val lines = listOf(count) + totals
        lines.forEachIndexed { index, line ->
            add(
                ReportStyle.Body,
                line,
                indent,
                spaceBefore = if (index == 0) 4f else 0f,
                keepWithNext = recorded > 0 || index < lines.lastIndex
            )
        }
        tracker.rows.forEach { row ->
            add(
                ReportStyle.Subheading,
                string(Res.string.tracker_row_title, definition.rowTitle, row.number),
                indent,
                spaceBefore = 4f,
                keepWithNext = row.values.isNotEmpty()
            )
            if (row.values.isNotEmpty()) add(ReportStyle.Body, valuesText(row.values), indent)
        }
    }

    /**
     * A rank's requirement that asks for merit badges: how many the scout has completed, and how
     * many of them count as Eagle-required, worded as on the requirement's page, then each badge
     * with the date it was completed on, and "Eagle-required" if it counts as Eagle-required
     * toward the requirement. The page's line on how many more are needed is left out, as the
     * report is on a rank that's already earned.
     */
    private suspend fun add(meritBadges: ReportMeritBadges, indent: Int) {
        val credit = meritBadges.credit
        val needed = credit.needed
        val counts = listOf(
            quantityString(
                Res.plurals.requirement_merit_badges_count,
                needed.total,
                credit.completed,
                needed.total
            ),
            quantityString(
                Res.plurals.requirement_eagle_required_count,
                needed.eagleRequired,
                credit.eagleRequired,
                needed.eagleRequired
            )
        )
        counts.forEachIndexed { index, line ->
            add(
                ReportStyle.Body,
                line,
                indent,
                spaceBefore = if (index == 0) 4f else 0f,
                keepWithNext = meritBadges.badges.isNotEmpty() || index < counts.lastIndex
            )
        }
        meritBadges.badges.forEach { earned ->
            val parts = listOfNotNull(
                earned.badge.name,
                completionText(Completion(earned.completedOn)),
                string(Res.string.requirement_merit_badge_eagle_required)
                    .takeIf { earned.countsAsEagleRequired(needed) }
            )
            add(ReportStyle.Body, parts.joinToString(valueSeparator), indent)
        }
    }

    /**
     * A row's values, each after its column's label, separated as on screen, such as by " · ". A
     * value after multi-line text starts a new line, so it doesn't read as the text's last line.
     */
    private suspend fun valuesText(values: List<Pair<TrackerColumn, String>>): String =
        buildString {
            values.forEachIndexed { index, (column, value) ->
                if (index > 0) {
                    val previous = values[index - 1].first.type
                    append(
                        if (previous == TrackerColumnType.MULTILINE_TEXT) "\n" else valueSeparator
                    )
                }
                val text = valueText(column, value)
                append(string(Res.string.report_labeled_value, column.label, text))
            }
        }

    /**
     * A tracker value as stored, with a date written out, as on screen. Multi-line text keeps
     * its line breaks, as notes do.
     */
    private fun valueText(column: TrackerColumn, value: String): String = when (column.type) {
        TrackerColumnType.DATE -> storedDate(value)?.let(::date) ?: typed(value)

        // The scout typed it.
        TrackerColumnType.NUMBER, TrackerColumnType.TEXT, TrackerColumnType.MULTILINE_TEXT ->
            typed(value)
    }

    private suspend fun completionText(completion: Completion?): String = when {
        completion == null -> string(Res.string.report_not_completed)
        completion.date == null -> string(Res.string.report_completed)
        else -> string(Res.string.report_completed_on, date(completion.date))
    }

    /** How the rank was earned, and when, as its page says. */
    private suspend fun earnedText(report: AdvancementReport): String {
        val earnedOn = report.completion?.date
        return when {
            report.earnedWith != null -> string(Res.string.report_earned_with, report.earnedWith)
            earnedOn == null -> string(Res.string.report_earned)
            else -> string(Res.string.report_earned_on, date(earnedOn))
        }
    }

    private suspend fun string(resource: StringResource, vararg args: Any): String =
        getString(environment, resource, *args)

    private suspend fun quantityString(
        resource: PluralStringResource,
        quantity: Int,
        vararg args: Any
    ): String = getPluralString(environment, resource, quantity, *args)

    // Text the scout typed keeps its own direction, as on screen. A line break ends a paragraph
    // for the bidi algorithm, and the wrapping with it, so each line is wrapped on its own.
    private fun typed(text: String): String =
        text.lines().joinToString("\n") { formats.typedText(it) }

    private fun date(date: LocalDate): String = formats.formatDate(date)

    private fun add(
        style: ReportStyle,
        text: String,
        indent: Int = 0,
        spaceBefore: Float = 0f,
        keepWithNext: Boolean = false
    ) {
        // What follows a heading sits the same distance under it, whatever it is.
        val space = if (paragraphs.lastOrNull()?.style == ReportStyle.Heading) 4f else spaceBefore
        paragraphs += Paragraph(text, style, indent, space, keepWithNext)
    }

    private suspend fun addHeading(text: StringResource) {
        add(ReportStyle.Heading, string(text), spaceBefore = 18f, keepWithNext = true)
    }

    private suspend fun addLabeled(label: StringResource, value: String, indent: Int = 0) {
        add(ReportStyle.Body, string(Res.string.report_labeled_value, string(label), value), indent)
    }

    /**
     * The paragraphs, placed on as many pages as they need, each with its page number, which
     * keeps printed pages in order. A paragraph that doesn't fit at the foot of a page continues
     * on the next one, unless it's kept with the paragraph after it.
     */
    suspend fun pages(): List<ReportPage<T>> {
        val layouts = paragraphs.map { layOut(it, it.text) }
        val pages = mutableListOf(mutableListOf<PlacedText<T>>())
        // The top of the next line.
        var y = MARGIN.toFloat()
        fun newPage() {
            pages += mutableListOf<PlacedText<T>>()
            y = MARGIN.toFloat()
        }
        fun place(layout: T, paragraph: Paragraph) {
            val x = if (typesetter.rightToLeft) MARGIN else MARGIN + paragraph.indent
            pages.last() += PlacedText(layout, x.toFloat(), y)
            y += layout.height
        }
        paragraphs.forEachIndexed { index, paragraph ->
            if (pages.last().isNotEmpty()) {
                y += paragraph.spaceBefore
                if (paragraph.keepWithNext && y + keptHeight(index, layouts) > CONTENT_BOTTOM) {
                    newPage()
                }
            }
            var layout = layouts[index]
            while (true) {
                val fitting = (0 until layout.lineCount).count {
                    y + layout.lineBottom(it) <= CONTENT_BOTTOM
                }
                if (fitting == layout.lineCount) break
                // Every line fits on an empty page, as text is at most 20 points high.
                if (fitting > 0) {
                    val text = layout.text
                    val split = layout.lineStart(fitting)
                    place(layOut(paragraph, text.substring(0, split)), paragraph)
                    val rest = text.substring(split)
                    // Typed text in another direction keeps it on the next page.
                    layout = layOut(paragraph, openDirections(text.substring(0, split)) + rest)
                }
                newPage()
            }
            place(layout, paragraph)
        }
        return pages.mapIndexed { index, texts ->
            val number = string(Res.string.report_page, index + 1, pages.size)
            val footer = typesetter.layOut(
                number,
                ReportStyle.Footer,
                CONTENT_WIDTH,
                centered = true
            )
            ReportPage(texts + PlacedText(footer, MARGIN.toFloat(), FOOTER_TOP.toFloat()))
        }
    }

    /**
     * The height that the paragraph at [index], which is kept with the next one, needs at the
     * foot of a page: all of it, and of each paragraph after it that's kept with the next, with
     * the spaces between them, then the first line of the next paragraph. So a heading followed
     * by a requirement's title, which is kept with its next line too, moves to the next page
     * with both.
     */
    private fun keptHeight(index: Int, layouts: List<T>): Float {
        var height = 0f
        var last = index
        while (paragraphs[last].keepWithNext && last < paragraphs.lastIndex) {
            height += layouts[last].height + paragraphs[last + 1].spaceBefore
            last++
        }
        // The last paragraph of a report has none after it.
        if (last == index) return layouts[index].height
        return height + layouts[last].lineBottom(0)
    }

    /** [text], all or part of [paragraph], laid out in its style. */
    private fun layOut(paragraph: Paragraph, text: String): T =
        typesetter.layOut(text, paragraph.style, CONTENT_WIDTH - paragraph.indent)
}

/**
 * The bidi embeddings, overrides and isolates that are still open at the end of [text], in
 * order, to start the rest of a split paragraph with. Text the scout typed in another direction
 * is wrapped in an embedding ([LocaleFormats.typedText]), so the rest of it keeps its direction
 * on the next page. A paragraph separator closes them all, as it ends a paragraph for the bidi
 * algorithm: a line break, or a separator such as U+2029 that notes can hold.
 */
internal fun openDirections(text: CharSequence): String {
    val open = ArrayDeque<Char>()
    for (char in text) {
        when (char) {
            in EMBEDDINGS, in ISOLATES -> open.addLast(char)

            // Closes the last embedding or override, but not one outside an isolate.
            POP_DIRECTIONAL_FORMATTING ->
                if (open.isNotEmpty() && open.last() in EMBEDDINGS) open.removeLast()

            // Closes the last isolate and everything opened inside it.
            POP_DIRECTIONAL_ISOLATE -> {
                val isolate = open.indexOfLast { it in ISOLATES }
                if (isolate >= 0) repeat(open.size - isolate) { open.removeLast() }
            }

            in PARAGRAPH_SEPARATORS -> open.clear()
        }
    }
    return open.joinToString("")
}

/**
 * The characters that end a paragraph for the bidi algorithm, which closes every embedding,
 * override and isolate there: those whose bidi class is B (Unicode's DerivedBidiClass.txt).
 */
private const val PARAGRAPH_SEPARATORS =
    "\n\r\u001C\u001D\u001E\u0085\u2029"

/** Left-to-right and right-to-left embeddings and overrides. */
private const val EMBEDDINGS = "\u202A\u202B\u202D\u202E"

/** Left-to-right, right-to-left and first-strong isolates. */
private const val ISOLATES = "\u2066\u2067\u2068"

private const val POP_DIRECTIONAL_FORMATTING = '\u202C'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'
