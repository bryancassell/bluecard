package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.text.PhoneNumberFormat

// What iOS doesn't have yet.

/** Leaves phone numbers as typed, until #350 chooses what formats them on iOS. */
internal object UnformattedPhoneNumbers : PhoneNumberFormat {
    override suspend fun format(number: String): String = number
}
