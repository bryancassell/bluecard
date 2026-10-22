package io.github.bryancassell.bluecard.text

import android.icu.text.ListFormatter
import android.telephony.PhoneNumberUtils
import androidx.core.text.BidiFormatter
import io.github.artificialpb.bignum.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * [LocaleFormats] in [locale], with Java's and Android's formatters. The screens get one in the
 * strings' locale ([stringsLocale]) from `ProvideStringsLanguageResources`; code outside Compose,
 * such as the PDF report, makes its own.
 */
class AndroidLocaleFormats(private val locale: Locale) : LocaleFormats {
    // Each is made when it's first needed: the PDF report, for one, never formats a list, and
    // ICU loads a locale's list patterns the first time.

    // kotlinx-datetime leaves localized formats out of its scope.
    private val dateFormatter by lazy {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withDecimalStyle(DecimalStyle.of(locale))
    }

    private val listFormatter by lazy { ListFormatter.getInstance(locale) }

    private val bidiFormatter by lazy { BidiFormatter.getInstance(locale) }

    override fun formatDate(date: LocalDate): String = dateFormatter.format(date.toJavaLocalDate())

    // A NumberFormat isn't safe to use on two threads at once, so each total gets its own.
    override fun formatTotal(total: BigDecimal): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            roundingMode = RoundingMode.DOWN
            isGroupingUsed = false
        }.format(total)

    override fun formatList(items: List<String>): String = listFormatter.format(items)

    override fun typedText(text: String): String = bidiFormatter.unicodeWrap(text)

    // PhoneNumberUtils formats with libphonenumber's formatInOriginalFormat, which keeps a number
    // typed with + in international form, returns the number as typed when formatting would
    // change a digit or it has no pattern for it, and null for one it can't read at all.
    override fun formatPhoneNumber(number: String): String =
        PhoneNumberUtils.formatNumber(number, "US") ?: number
}
