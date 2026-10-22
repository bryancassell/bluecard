package io.github.bryancassell.bluecard.text

import io.github.artificialpb.bignum.BigDecimal
import kotlinx.datetime.LocalDate

/**
 * Writes dates, numbers, lists and text the scout typed by one locale's rules, which come from the
 * platform: Kotlin's common libraries don't have them (#341). The screens and the PDF report use
 * one in the strings' locale, so a sentence never mixes two languages (ARCHITECTURE.md, Language
 * and layout direction). Behavior every implementation must have is in `LocaleFormatsContract`.
 */
interface LocaleFormats {
    /**
     * A date something was done on, such as a requirement's completion date: "Apr 15, 2026",
     * with the locale's digits.
     */
    fun formatDate(date: LocalDate): String

    /**
     * A tracker column's values added up, such as the 4.5 in "4.5 of 6 hours", with the locale's
     * digits and decimal separator. It has up to two decimal places, rounded down, so a total
     * never reads as reaching the amount before it does. Its digits aren't grouped, like the
     * amount after it, and because the scout can type a comma as a decimal separator.
     */
    fun formatTotal(total: BigDecimal): String

    /** [items] as a list inside a sentence, such as "Cycling, Hiking, and Swimming". */
    fun formatList(items: List<String>): String

    /**
     * [text] the scout typed, to show on its own or inside one of the app's strings. It's wrapped
     * so it keeps its own direction within the locale's: a Persian name keeps its final period
     * at its end in an English sentence.
     */
    fun typedText(text: String): String

    /**
     * A phone [number] the scout typed, formatted to read at a glance: "(555) 123-4567" for
     * 5551234567. One without a country code follows US rules in every locale, since most
     * counselors' numbers are American and another country's rules would group one wrongly
     * (#365). One that can't be formatted, such as a UK number without +44, comes back as typed.
     * Its digits never change, only the characters between them.
     */
    fun formatPhoneNumber(number: String): String
}
