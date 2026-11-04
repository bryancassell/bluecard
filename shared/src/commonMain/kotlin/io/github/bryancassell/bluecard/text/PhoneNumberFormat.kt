package io.github.bryancassell.bluecard.text

/**
 * Formats phone numbers the scout typed by US rules, which come from libphonenumber: Kotlin's
 * common libraries don't have them. Unlike [LocaleFormats], they don't depend on the strings'
 * language. Behavior every implementation must have is in `PhoneNumberFormatContract`.
 */
interface PhoneNumberFormat {
    /**
     * [number], formatted to read at a glance: "(555) 123-4567" for 5551234567. One without a
     * country code follows US rules in every locale (PRD.md, Counselor phone numbers). Only
     * spaces and punctuation change, so a number with anything else in it, such as
     * "Cell: 555-123-4567", comes back as typed. So does one that formatting would join digits of
     * that were typed apart, and one that can't be formatted, such as a UK number without +44.
     *
     * Safe to call from the main thread, though the platform's phone number data may be read
     * from disk.
     */
    suspend fun format(number: String): String
}

/**
 * [number] as [formatInOriginalFormat] writes it, where that changes only its spaces and
 * punctuation, as [PhoneNumberFormat.format] promises. Otherwise, and where it returns null for a
 * number it can't read, [number] as typed.
 *
 * [formatInOriginalFormat] is libphonenumber's, with US rules, which keeps a number typed with +
 * in international form. It skips text before the number and some after it, so it would drop a
 * label such as "Cell: ". And with no international format for 7 US digits, +1 555-0100 comes
 * back as +1 5550100. Both are left as typed, and so is a number it adds words to, such as
 * " ext. " before digits it reads as an extension.
 */
internal fun formatOnlyPunctuation(
    number: String,
    formatInOriginalFormat: (String) -> String?
): String {
    if (!isPhoneNumberText(number)) return number
    val formatted = formatInOriginalFormat(number) ?: return number
    val keepsDigitBreaks = digitBreaks(formatted).containsAll(digitBreaks(number))
    return if (isPhoneNumberText(formatted) && keepsDigitBreaks) formatted else number
}

/** Whether [text] has only digits, spaces and phone punctuation. */
private fun isPhoneNumberText(text: String): Boolean =
    text.all { it in '0'..'9' || it in "+-.()/" || it.isWhitespace() }

private val DIGITS = Regex("[0-9]+")

/**
 * Where [text]'s groups of digits end, but for the last, counted in digits: {3, 6} for
 * "555-123-4567". Formatting changes no digit, so a break typed and missing from the formatted
 * number means it joined digits typed apart.
 */
private fun digitBreaks(text: String): Set<Int> =
    DIGITS.findAll(text).map { it.value.length }.runningReduce(Int::plus).toList().dropLast(1)
        .toSet()
