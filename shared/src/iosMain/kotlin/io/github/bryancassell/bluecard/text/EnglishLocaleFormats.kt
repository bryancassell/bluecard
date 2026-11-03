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
        text.firstNotNullOfOrNull(::strongDirection) == Direction.RightToLeft ->
            "$LRM$RLE$text$PDF$LRM"

        (text.lastIndex downTo 0).firstNotNullOfOrNull { strongDirection(text[it]) } ==
            Direction.RightToLeft -> "$text$LRM"

        else -> text
    }

    private enum class Direction { LeftToRight, RightToLeft }

    // A letter is right-to-left in the Hebrew, Arabic and other right-to-left blocks, and
    // left-to-right elsewhere. Digits, punctuation and spaces have no direction of their own.
    // Common Kotlin has no Unicode bidi classes (Char.directionality is the JVM's).
    private fun strongDirection(char: Char): Direction? = when {
        !char.isLetter() -> null
        RIGHT_TO_LEFT_BLOCKS.any { char in it } -> Direction.RightToLeft
        else -> Direction.LeftToRight
    }

    // Hebrew to Arabic Extended-A, and the Hebrew and Arabic presentation forms.
    private val RIGHT_TO_LEFT_BLOCKS =
        listOf('\u0590'..'\u08FF', '\uFB1D'..'\uFDFF', '\uFE70'..'\uFEFF')

    private const val LRM = '\u200E'
    private const val RLE = '\u202B'
    private const val PDF = '\u202C'
}
