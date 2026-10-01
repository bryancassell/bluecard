package io.github.bryancassell.bluecard.data.report

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.annotation.StringRes
import androidx.core.graphics.withTranslation
import androidx.core.text.BidiFormatter
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.data.catalog.TrackerColumn
import io.github.bryancassell.bluecard.data.catalog.TrackerColumnType
import io.github.bryancassell.bluecard.data.progress.Completion
import io.github.bryancassell.bluecard.data.progress.storedDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle

// A badge's report, laid out on US Letter pages. Sizes are in PDF points, 1/72 of an inch.

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

/** One page of a report: blocks of text, each at its place on the page. */
class ReportPage internal constructor(private val texts: List<PlacedText>) {
    /** The page's text, a line at a time, in the order it's laid out. */
    val lines: List<String> get() = texts.flatMap { it.lines() }

    /** Draws the page onto [canvas], which is [PAGE_WIDTH] by [PAGE_HEIGHT]. */
    fun draw(canvas: Canvas) {
        texts.forEach { it.draw(canvas) }
    }
}

/** A block of text laid out by [layout], with its top left corner at ([x], [y]). */
internal class PlacedText(
    private val layout: StaticLayout,
    private val x: Float,
    private val y: Float
) {
    fun lines(): List<String> = List(layout.lineCount) {
        layout.text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim()
    }

    fun draw(canvas: Canvas) {
        canvas.withTranslation(x, y) { layout.draw(this) }
    }
}

/**
 * Lays [report] out on pages, with its text from [resources], which must be in the strings'
 * language (`stringsLanguageResources`). Dates and numbers are formatted in their first
 * locale, and text is laid out in its direction (ARCHITECTURE.md, UI layer).
 */
fun layOutReport(report: BadgeReport, resources: Resources): List<ReportPage> =
    ReportComposer(resources).apply { add(report) }.pages()

/** The text styles of a report. */
private enum class Style(val size: Float, val bold: Boolean) {
    Title(20f, true),
    Heading(14f, true),
    Subheading(11f, true),
    Body(11f, false),
    Footer(9f, false)
}

/** A paragraph of a report, before it's placed on a page. */
private class Paragraph(
    val text: String,
    val style: Style,
    val indent: Int,
    /** Space above it, unless it starts a page. */
    val spaceBefore: Float,
    /** Whether it goes on the next page with the first line of the paragraph after it. */
    val keepWithNext: Boolean
)

/** Writes a report's paragraphs, then places them on pages. */
private class ReportComposer(private val resources: Resources) {
    private val locale = resources.configuration.locales[0]
    private val rightToLeft =
        TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL

    // The same format as dates on screen (rememberCompletionDateFormatter): "Apr 15, 2026".
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale))

    // Text the scout typed keeps its own direction (ARCHITECTURE.md, UI layer).
    private val bidiFormatter = BidiFormatter.getInstance(locale)

    private val paints = Style.entries.associateWith { style ->
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = style.size
            typeface = if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = Color.BLACK
            textLocale = locale
        }
    }

    private val paragraphs = mutableListOf<Paragraph>()

    fun add(report: BadgeReport) {
        val profile = report.profile
        add(Style.Body, string(R.string.report_kind))
        add(Style.Title, report.badgeName, spaceBefore = 2f)
        add(Style.Body, string(R.string.report_scout, typed(profile.name)), spaceBefore = 8f)
        add(Style.Body, string(R.string.report_unit, typed(profile.unitNumber)))
        add(Style.Body, completionText(report.completion))
        val version = date(report.requirementsVersion)
        add(Style.Body, string(R.string.report_requirements_version, version))
        add(Style.Body, string(R.string.report_created_on, date(report.createdDate)))
        report.counselor?.let { counselor ->
            addHeading(R.string.report_counselor)
            counselor.name?.let { addLabeled(R.string.report_counselor_name, typed(it)) }
            counselor.phone?.let { addLabeled(R.string.report_counselor_phone, typed(it)) }
            counselor.email?.let { addLabeled(R.string.report_counselor_email, typed(it)) }
        }
        addHeading(R.string.report_requirements)
        report.requirements.forEach { add(it, depth = 0) }
    }

    private fun add(requirement: ReportRequirement, depth: Int) {
        val indent = depth * INDENT
        add(
            Style.Subheading,
            string(R.string.report_requirement, requirement.number, requirement.summary),
            indent,
            spaceBefore = 10f,
            keepWithNext = true
        )
        requirement.requiredCount?.let {
            val choice = string(R.string.requirement_choice, it, requirement.children.size)
            add(Style.Body, choice, indent)
        }
        add(Style.Body, completionText(requirement.completion), indent)
        requirement.comment?.let { addLabeled(R.string.report_comment, typed(it), indent) }
        requirement.tracker?.let { add(it, indent) }
        requirement.children.forEach { add(it, depth + 1) }
    }

    private fun add(tracker: ReportTracker, indent: Int) {
        val definition = tracker.definition
        val recorded = tracker.rows.size
        // As on the requirement's page (TrackerDefinition.count): "5 sessions" in a log, or
        // "8 of 12 weeks". The catalog is in English, so its row labels follow English plurals.
        val rows = if ((definition.rowCount ?: recorded) == 1) {
            definition.rowLabel
        } else {
            definition.rowLabelPlural
        }
        val count = definition.rowCount?.let {
            string(R.string.tracker_count_of, recorded, it, rows)
        } ?: string(R.string.tracker_count, recorded, rows)
        add(Style.Body, count, indent, spaceBefore = 4f, keepWithNext = recorded > 0)
        // Capitalized for titles, as TrackerDefinition.rowTitle is.
        val rowTitle = definition.rowLabel.replaceFirstChar { it.titlecase() }
        tracker.rows.forEach { row ->
            add(
                Style.Subheading,
                string(R.string.tracker_row_title, rowTitle, row.number),
                indent,
                spaceBefore = 4f,
                keepWithNext = row.values.isNotEmpty()
            )
            if (row.values.isNotEmpty()) {
                val values = row.values.joinToString(" · ") { (column, value) ->
                    string(R.string.report_labeled_value, column.label, valueText(column, value))
                }
                add(Style.Body, values, indent)
            }
        }
    }

    /** A tracker value as stored, with a date written out, as on screen. */
    private fun valueText(column: TrackerColumn, value: String): String = when (column.type) {
        TrackerColumnType.DATE -> storedDate(value)?.let(::date) ?: typed(value)

        // The scout typed it.
        TrackerColumnType.NUMBER, TrackerColumnType.TEXT -> typed(value)
    }

    private fun completionText(completion: Completion?): String = when {
        completion == null -> string(R.string.report_not_completed)
        completion.date == null -> string(R.string.report_completed)
        else -> string(R.string.report_completed_on, date(completion.date))
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        resources.getString(id, *args)

    private fun typed(text: String): String = bidiFormatter.unicodeWrap(text)

    private fun date(date: LocalDate): String = dateFormatter.format(date)

    private fun add(
        style: Style,
        text: String,
        indent: Int = 0,
        spaceBefore: Float = 0f,
        keepWithNext: Boolean = false
    ) {
        // What follows a heading sits the same distance under it, whatever it is.
        val space = if (paragraphs.lastOrNull()?.style == Style.Heading) 4f else spaceBefore
        paragraphs += Paragraph(text, style, indent, space, keepWithNext)
    }

    private fun addHeading(@StringRes text: Int) {
        add(Style.Heading, string(text), spaceBefore = 18f, keepWithNext = true)
    }

    private fun addLabeled(@StringRes label: Int, value: String, indent: Int = 0) {
        add(Style.Body, string(R.string.report_labeled_value, string(label), value), indent)
    }

    /**
     * The paragraphs, placed on as many pages as they need, each with its page number. A
     * paragraph that doesn't fit at the foot of a page continues on the next one, unless it's
     * kept with the paragraph after it.
     */
    fun pages(): List<ReportPage> {
        val layouts = paragraphs.map { layOut(it, it.text) }
        val pages = mutableListOf(mutableListOf<PlacedText>())
        // The top of the next line.
        var y = MARGIN.toFloat()
        fun newPage() {
            pages += mutableListOf<PlacedText>()
            y = MARGIN.toFloat()
        }
        fun place(layout: StaticLayout, paragraph: Paragraph) {
            val x = if (rightToLeft) MARGIN else MARGIN + paragraph.indent
            pages.last() += PlacedText(layout, x.toFloat(), y)
            y += layout.height
        }
        paragraphs.forEachIndexed { index, paragraph ->
            if (pages.last().isNotEmpty()) {
                y += paragraph.spaceBefore
                if (paragraph.keepWithNext && index < paragraphs.lastIndex) {
                    val next = paragraphs[index + 1]
                    val withNext = layouts[index].height + next.spaceBefore +
                        layouts[index + 1].getLineBottom(0)
                    if (y + withNext > CONTENT_BOTTOM) newPage()
                }
            }
            var layout = layouts[index]
            while (true) {
                val fitting = (0 until layout.lineCount).count {
                    y + layout.getLineBottom(it) <= CONTENT_BOTTOM
                }
                if (fitting == layout.lineCount) break
                // Every line fits on an empty page, as text is at most 20 points high.
                if (fitting > 0) {
                    val text = layout.text
                    val split = layout.getLineStart(fitting)
                    place(layOut(paragraph, text.subSequence(0, split)), paragraph)
                    layout = layOut(paragraph, text.subSequence(split, text.length))
                }
                newPage()
            }
            place(layout, paragraph)
        }
        return pages.mapIndexed { index, texts ->
            val number = string(R.string.report_page, index + 1, pages.size)
            val footer = layOut(number, Style.Footer, CONTENT_WIDTH, Layout.Alignment.ALIGN_CENTER)
            ReportPage(texts + PlacedText(footer, MARGIN.toFloat(), FOOTER_TOP.toFloat()))
        }
    }

    /** [text], all or part of [paragraph], laid out in its style. */
    private fun layOut(paragraph: Paragraph, text: CharSequence): StaticLayout =
        layOut(text, paragraph.style, CONTENT_WIDTH - paragraph.indent)

    private fun layOut(
        text: CharSequence,
        style: Style,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        val direction =
            if (rightToLeft) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        // The builder's defaults, simple line breaks without hyphens, depend only on the text
        // before each break, so the rest of a paragraph that's split across pages breaks the
        // same way on its own.
        return StaticLayout.Builder.obtain(text, 0, text.length, paints.getValue(style), width)
            .setAlignment(alignment)
            .setTextDirection(direction)
            .setIncludePad(false)
            .build()
    }
}
