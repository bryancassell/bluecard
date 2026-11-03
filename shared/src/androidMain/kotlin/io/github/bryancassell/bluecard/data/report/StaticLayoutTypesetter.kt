package io.github.bryancassell.bluecard.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.withTranslation
import androidx.core.text.layoutDirection
import io.github.bryancassell.bluecard.text.AndroidLocaleFormats
import io.github.bryancassell.bluecard.text.LocaleFormats
import java.util.Locale

/** A report's text laid out by Android's [StaticLayout]. */
class StaticLayoutText internal constructor(
    override val text: String,
    private val layout: StaticLayout
) : ReportText {
    override val lineCount: Int get() = layout.lineCount

    override fun lineStart(line: Int): Int = layout.getLineStart(line)

    override fun lineBottom(line: Int): Float = layout.getLineBottom(line).toFloat()

    fun draw(canvas: Canvas) {
        layout.draw(canvas)
    }
}

/** Lays a report's text out with Android's [StaticLayout], in [locale]. */
class StaticLayoutTypesetter(private val locale: Locale) : ReportTypesetter<StaticLayoutText> {
    override val formats: LocaleFormats = AndroidLocaleFormats(locale)

    override val rightToLeft: Boolean = locale.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private val paints = ReportStyle.entries.associateWith { style ->
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = style.size
            typeface = if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = Color.BLACK
            textLocale = locale
        }
    }

    override fun layOut(
        text: String,
        style: ReportStyle,
        width: Int,
        centered: Boolean
    ): StaticLayoutText {
        val direction =
            if (rightToLeft) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        val alignment =
            if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val paint = paints.getValue(style)
        // The builder's defaults, simple line breaks without hyphens, depend only on the text
        // before each break, as ReportTypesetter asks.
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setTextDirection(direction)
            .setIncludePad(false)
            .build()
        return StaticLayoutText(text, layout)
    }
}

/** Draws the page onto [canvas], which is [PAGE_WIDTH] by [PAGE_HEIGHT]. */
fun ReportPage<StaticLayoutText>.draw(canvas: Canvas) {
    texts.forEach { placed ->
        canvas.withTranslation(placed.x, placed.y) { placed.text.draw(this) }
    }
}
