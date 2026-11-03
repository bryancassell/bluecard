package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.RoundingMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char

/**
 * [LocaleFormats] in US English, the strings' only language on iOS, written in plain Kotlin as
 * Android's formatters write it. #350 replaces it with Foundation's formatters in the strings'
 * locale.
 */
internal object EnglishLocaleFormats : LocaleFormats {
    private val dateFormat = LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(padding = Padding.NONE)
        chars(", ")
        year()
    }

    override fun formatDate(date: LocalDate): String = dateFormat.format(date)

    override fun formatTotal(total: BigDecimal): String =
        total.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString()

    override fun formatList(items: List<String>): String = if (items.size <= 2) {
        items.joinToString(" and ")
    } else {
        items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }

    // As Android's BidiFormatter wraps it in English: text that starts right-to-left is embedded
    // as right-to-left, with a left-to-right mark on each side, so a Persian name keeps its final
    // period at its end. Other text that ends right-to-left gets a mark after it, so what follows
    // isn't drawn into it.
    override fun typedText(text: String): String = when {
        codePoints(text).firstNotNullOfOrNull(::strongDirection) == Direction.RightToLeft ->
            "$LRM$RLE$text$PDF$LRM"

        codePoints(text).mapNotNull(::strongDirection).lastOrNull() == Direction.RightToLeft ->
            "$text$LRM"

        else -> text
    }

    private enum class Direction { LeftToRight, RightToLeft }

    // The direction a character sets, as Unicode's bidi classes would give it, which common Kotlin
    // doesn't have (Char.directionality is the JVM's). A letter is right-to-left in the Hebrew,
    // Arabic and other right-to-left blocks, and left-to-right elsewhere, and so are the marks
    // that set a direction. Digits, punctuation and spaces set none. Char.isLetter knows only the
    // Basic Multilingual Plane, so beyond it only the right-to-left blocks count, such as Adlam's.
    private fun strongDirection(codePoint: Int): Direction? = when {
        codePoint == LRM.code -> Direction.LeftToRight

        codePoint == RLM || codePoint == ALM -> Direction.RightToLeft

        codePoint > 0xFFFF ->
            if (RIGHT_TO_LEFT_BLOCKS.any { codePoint in it }) Direction.RightToLeft else null

        !codePoint.toChar().isLetter() -> null

        RIGHT_TO_LEFT_BLOCKS.any { codePoint in it } -> Direction.RightToLeft

        else -> Direction.LeftToRight
    }

    private fun codePoints(text: String): Sequence<Int> = sequence {
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                yield(0x10000 + ((char.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00))
                i += 2
            } else {
                yield(char.code)
                i++
            }
        }
    }

    // Hebrew to Arabic Extended-A, the Hebrew and Arabic presentation forms, and the blocks Unicode
    // keeps for right-to-left scripts beyond the Basic Multilingual Plane.
    private val RIGHT_TO_LEFT_BLOCKS =
        listOf(0x0590..0x08FF, 0xFB1D..0xFDFF, 0xFE70..0xFEFF, 0x10800..0x10FFF, 0x1E800..0x1EFFF)

    // The right-to-left and Arabic letter marks.
    private const val RLM = 0x200F
    private const val ALM = 0x061C

    private const val LRM = '\u200E'
    private const val RLE = '\u202B'
    private const val PDF = '\u202C'
}
