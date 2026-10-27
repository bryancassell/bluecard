package io.github.bryancassell.bluecard.text

/**
 * Formats phone numbers the scout typed by US rules, which come from the platform: Kotlin's
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
