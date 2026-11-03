package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.RoundingMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
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
import platform.Foundation.NSTimeZone
import platform.Foundation.canonicalLanguageIdentifierFromString
import platform.Foundation.characterDirectionForLanguage
import platform.Foundation.localeIdentifier
import platform.Foundation.timeZoneForSecondsFromGMT

/**
 * [LocaleFormats] in [locale], with Foundation's formatters. The screens get one in the strings'
 * locale ([stringsLocale]) from `BlueCardApp` (`rememberLocaleFormats`).
 */
class IosLocaleFormats(private val locale: NSLocale) : LocaleFormats {
    // Each is made when it's first needed, as on Android. Apple documents its date and number
    // formatters as thread-safe since iOS 7, so one of each serves every call.

    // The Gregorian calendar, which the dates scouts record are in, whatever the locale's own
    // calendar is, as Android's java.time writes them. Iran's, for one, is the Persian calendar,
    // so without this, en_IR writes Apr 15, 2026 as "Farvardin 26, 1405 AP".
    private val gregorian by lazy {
        NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)!!.apply {
            timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
        }
    }

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

    override fun formatDate(date: LocalDate): String {
        val day = NSDateComponents().apply {
            year = date.year.toLong()
            month = date.month.number.toLong()
            this.day = date.day.toLong()
        }
        return dateFormatter.stringFromDate(gregorian.dateFromComponents(day)!!)
    }

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

    // By the locale's language and script, since the script can change it: Punjabi is written
    // left-to-right, but right-to-left in its Arabic script (pa-Arab).
    private val rightToLeft by lazy {
        NSLocale.characterDirectionForLanguage(
            NSLocale.canonicalLanguageIdentifierFromString(locale.localeIdentifier)
        ) == NSLocaleLanguageDirectionRightToLeft
    }

    // As Android's BidiFormatter wraps it: text whose first letter is in the other direction from
    // the locale's is embedded in its own, with a mark of the locale's direction on each side, so
    // a Persian name keeps its final period at its end in an English sentence. Text with no letter
    // counts as left-to-right. Other text that ends in the other direction gets a mark after it, so
    // what follows isn't drawn into it.
    override fun typedText(text: String): String {
        val directions = codePoints(text).mapNotNull(::strongDirection).toList()
        val context = if (rightToLeft) Direction.RightToLeft else Direction.LeftToRight
        val mark = if (rightToLeft) RLM else LRM
        val first = directions.firstOrNull() ?: Direction.LeftToRight
        return when {
            first != context -> {
                val embedding = if (first == Direction.RightToLeft) RLE else LRE
                "$mark$embedding$text$PDF$mark"
            }

            directions.lastOrNull().let { it != null && it != context } -> "$text$mark"

            else -> text
        }
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
