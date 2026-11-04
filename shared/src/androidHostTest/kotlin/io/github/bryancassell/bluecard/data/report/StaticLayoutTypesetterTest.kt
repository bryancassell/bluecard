package io.github.bryancassell.bluecard.data.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.withTranslation
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Lays text out with StaticLayout and draws it into a bitmap, with Robolectric's native graphics,
 * which measure, break and draw text with real fonts, as a phone does. What every typesetter must
 * do is in [ReportTypesetterContract].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaticLayoutTypesetterTest : ReportTypesetterContract<StaticLayoutText>() {
    override val typesetter = StaticLayoutTypesetter(Locale.US)
    override val rightToLeftTypesetter = StaticLayoutTypesetter(Locale.forLanguageTag("fa-IR"))

    @Test
    fun formats_andDirection_areTheLocales() {
        assertFalse(typesetter.rightToLeft)
        assertTrue(rightToLeftTypesetter.rightToLeft)
        assertEquals("Sep 30, 2026", typesetter.formats.formatDate(LocalDate(2026, 9, 30)))
    }

    // As PdfDocumentWriter draws a page, on its canvas.
    override fun ink(text: StaticLayoutText, x: Float, y: Float): Ink {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.withTranslation(x, y) { text.draw(this) }
        val pixels = IntArray(PAGE_WIDTH * PAGE_HEIGHT)
        bitmap.getPixels(pixels, 0, PAGE_WIDTH, 0, 0, PAGE_WIDTH, PAGE_HEIGHT)
        return Ink.of(PAGE_WIDTH, PAGE_HEIGHT) { column, row ->
            255 - Color.red(pixels[row * PAGE_WIDTH + column])
        }
    }
}
