package io.github.bryancassell.bluecard.text

import io.github.aughtone.phonenumber.PhoneNumberUtil

/**
 * [PhoneNumberFormat] with Aught One's Kotlin port of libphonenumber, since iOS has no phone
 * number formatter of its own. The port has the `formatInOriginalFormat` that Android's
 * `PhoneNumberUtils` calls, and Google's data, so iOS formats numbers as Android does.
 *
 * Google's Objective-C port, libPhoneNumber-iOS, would need CocoaPods, whose central repository
 * stops taking new versions on 2026-12-02, or Kotlin's SwiftPM import, which is Alpha and needs
 * Kotlin 2.5 (#350).
 *
 * Unlike Android's, it formats on the calling thread: the port's data is compiled in, not read
 * from disk. In a test on the iOS simulator, its first number took 2 ms and later ones 0.1 ms.
 */
internal object IosPhoneNumberFormat : PhoneNumberFormat {
    override suspend fun format(number: String): String = formatOnlyPunctuation(number) {
        try {
            PhoneNumberUtil.formatInOriginalFormat(
                PhoneNumberUtil.parseAndKeepRawInput(it, "US"),
                regionCallingFrom = "US"
            )
        } catch (_: PhoneNumberUtil.NumberParseException) {
            null
        }
    }
}
