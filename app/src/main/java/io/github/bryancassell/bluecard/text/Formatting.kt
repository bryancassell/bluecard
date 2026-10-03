package io.github.bryancassell.bluecard.text

import androidx.core.text.BidiFormatter
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

// Formats shared by the screens and the PDF report, so both write dates and the scout's text
// the same way. Compose code uses the functions that build on these: ui/badge/CompletionDate.kt's
// rememberCompletionDateFormatter and ui/TypedText.kt's typedText.

/**
 * Formats a date something was done on, such as a requirement's completion date: "Apr 15,
 * 2026", in [locale], the strings' locale ([stringsLocale]), with its digits
 * (ARCHITECTURE.md, Language and layout direction).
 */
fun completionDateFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale))

/**
 * Writes [total], a tracker column's values added up, such as the 4.5 in "4.5 of 6 hours", in
 * [locale], the strings' locale ([stringsLocale]), with its digits and decimal separator, and as
 * many decimal places as the values have.
 */
fun formatTotal(total: BigDecimal, locale: Locale): String = NumberFormat.getNumberInstance(locale)
    .apply { maximumFractionDigits = maxOf(total.scale(), 0) }
    .format(total)

/**
 * [text] the scout typed, to show on its own or inside one of the app's strings, which are in
 * [locale] ([stringsLocale]). It's wrapped so it keeps its own direction within the strings'
 * language's: a Persian name keeps its final period at its end in an English sentence
 * (ARCHITECTURE.md, Language and layout direction).
 */
fun typedText(text: String, locale: Locale): String =
    BidiFormatter.getInstance(locale).unicodeWrap(text)
