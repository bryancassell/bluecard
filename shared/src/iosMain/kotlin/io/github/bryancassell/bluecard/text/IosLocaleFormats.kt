package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.RoundingMode
import kotlinx.datetime.LocalDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSListFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleDecimalSeparator
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.canonicalLanguageIdentifierFromString
import platform.Foundation.characterDirectionForLanguage
import platform.Foundation.localeIdentifier

/**
 * [LocaleFormats] in [locale], with Foundation's formatters. The screens get one in the strings'
 * locale ([stringsLocale]) from `BlueCardApp` (`rememberLocaleFormats`).
 */
class IosLocaleFormats(private val locale: NSLocale) : LocaleFormats {
    // Each is made when it's first needed, as on Android. Apple documents its date and number
    // formatters as thread-safe since iOS 7, so one of each serves every call.

    private val gregorian by lazy { gregorianCalendar() }

    private val dateFormatter by lazy {
        NSDateFormatter().apply {
            locale = this@IosLocaleFormats.locale
            calendar = gregorian
            timeZone = gregorian.timeZone
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterNoStyle
        }
    }

    private val numberFormatter by lazy {
        NSNumberFormatter().apply {
            locale = this@IosLocaleFormats.locale
            numberStyle = NSNumberFormatterDecimalStyle
            usesGroupingSeparator = false
            maximumFractionDigits = 2u
        }
    }

    private val listFormatter by lazy {
        NSListFormatter().apply { locale = this@IosLocaleFormats.locale }
    }

    override fun formatDate(date: LocalDate): String =
        dateFormatter.stringFromDate(date.toNSDate(gregorian))

    // Rounded down here, exactly, since the formatter may round in binary floating point, where
    // 0.29 is just under 0.29 and would round down to 0.28. Rounded to the nearest, as it is by
    // default, a value with two decimal places comes back as it is. The decimal separator is set,
    // since NSDecimalNumber would otherwise read the text by the current locale's.
    override fun formatTotal(total: BigDecimal): String {
        val rounded = total.setScale(2, RoundingMode.DOWN).toPlainString()
        val number =
            NSDecimalNumber(string = rounded, locale = mapOf(NSLocaleDecimalSeparator to "."))
        return numberFormatter.stringFromNumber(number)!!
    }

    override fun formatList(items: List<String>): String =
        listFormatter.stringFromItems(items) ?: ""

    private val rightToLeft by lazy { isRightToLeft(locale.localeIdentifier) }

    // As Android's BidiFormatter.unicodeWrap wraps it. Text in the other direction from the
    // locale's is embedded in its own, with a mark of the locale's direction on each side, so a
    // Persian name keeps its final period at its end in an English sentence. Its direction is that
    // of its first UTF-16 unit that has one, as BidiFormatter's first-strong heuristic reads it. A
    // surrogate's is left-to-right, so text that starts beyond the Basic Multilingual Plane, as
    // with an emoji, isn't embedded, and text with no direction counts as left-to-right. Other text
    // gets a mark before it if its first code point with a direction is the other one, and after
    // it if its last is, so what's around it isn't drawn into it.
    override fun typedText(text: String): String {
        val context = if (rightToLeft) Direction.RightToLeft else Direction.LeftToRight
        val other = if (rightToLeft) Direction.LeftToRight else Direction.RightToLeft
        val mark = if (rightToLeft) RLM else LRM
        val direction = text.firstNotNullOfOrNull {
            if (it.isSurrogate()) Direction.LeftToRight else strongDirection(it.code)
        } ?: Direction.LeftToRight
        val embedded = direction != context
        val markBefore =
            embedded || codePoints(text).firstNotNullOfOrNull(::strongDirection) == other
        val markAfter = embedded || lastStrongDirection(text) == other
        val embedding = if (direction == Direction.RightToLeft) RLE else LRE
        return (if (markBefore) "$mark" else "") +
            (if (embedded) "$embedding$text$PDF" else text) +
            (if (markAfter) "$mark" else "")
    }

    // The direction of the last code point of [text] that has one, read from its end.
    private fun lastStrongDirection(text: String): Direction? {
        var end = text.length
        while (end > 0) {
            val start = if (end >= 2 && isSurrogatePairAt(text, end - 2)) end - 2 else end - 1
            strongDirection(codePointAt(text, start))?.let { return it }
            end = start
        }
        return null
    }

    private enum class Direction { LeftToRight, RightToLeft }

    // The direction a character sets, as Unicode's bidi classes would give it, which common Kotlin
    // doesn't have (Char.directionality is the JVM's). A letter is right-to-left in the Hebrew,
    // Arabic and other right-to-left blocks, and left-to-right elsewhere, and so are the marks,
    // embeddings and overrides that set a direction, which BidiFormatter's first-strong heuristic
    // counts too. Digits, punctuation and spaces set none. Char.isLetter knows only the Basic
    // Multilingual Plane, so beyond it Foundation tells a letter, such as U+20000, a CJK one.
    private fun strongDirection(codePoint: Int): Direction? = when {
        codePoint == LRM.code || codePoint == LRE.code || codePoint == LRO ->
            Direction.LeftToRight

        codePoint == RLM.code || codePoint == ALM || codePoint == RLE.code || codePoint == RLO ->
            Direction.RightToLeft

        codePoint > 0xFFFF -> when {
            RIGHT_TO_LEFT_BLOCKS.any { codePoint in it } -> Direction.RightToLeft
            isLetter(codePoint) -> Direction.LeftToRight
            else -> null
        }

        !codePoint.toChar().isLetter() -> null

        RIGHT_TO_LEFT_BLOCKS.any { codePoint in it } -> Direction.RightToLeft

        else -> Direction.LeftToRight
    }

    private companion object {
        // Hebrew to Arabic Extended-A, the Hebrew and Arabic presentation forms, and the blocks
        // Unicode keeps for right-to-left scripts beyond the Basic Multilingual Plane.
        val RIGHT_TO_LEFT_BLOCKS =
            listOf(
                0x0590..0x08FF,
                0xFB1D..0xFDFF,
                0xFE70..0xFEFF,
                0x10800..0x10FFF,
                0x1E800..0x1EFFF
            )

        // The left-to-right and right-to-left marks and embeddings, the pop that ends an
        // embedding, the Arabic letter mark, and the overrides.
        val LRM = Char(0x200E)
        val RLM = Char(0x200F)
        val LRE = Char(0x202A)
        val RLE = Char(0x202B)
        val PDF = Char(0x202C)
        const val ALM = 0x061C
        const val LRO = 0x202D
        const val RLO = 0x202E
    }
}

/**
 * Whether [locale], a locale identifier or BCP 47 tag, is written right-to-left. Its script
 * counts as well as its language: Punjabi is written left-to-right, but right-to-left in its
 * Arabic script, pa-Arab.
 */
internal fun isRightToLeft(locale: String): Boolean = NSLocale.characterDirectionForLanguage(
    NSLocale.canonicalLanguageIdentifierFromString(locale)
) == NSLocaleLanguageDirectionRightToLeft
