package io.github.bryancassell.bluecard.text

import io.github.aughtone.phonenumber.PhoneNumberUtil
import io.github.bryancassell.bluecard.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [PhoneNumberFormat] with Aught One's Kotlin port of libphonenumber, on [ioDispatcher], since
 * iOS has no phone number formatter of its own. The port has the `formatInOriginalFormat` that
 * Android's `PhoneNumberUtils` calls, and Google's data, so iOS formats numbers as Android does.
 *
 * Google's Objective-C port, libPhoneNumber-iOS, would need CocoaPods, whose central repository
 * stops taking new versions on 2026-12-02, or Kotlin's SwiftPM import, which is Alpha and needs
 * Kotlin 2.5 (#350).
 */
class IosPhoneNumberFormat(@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher) :
    PhoneNumberFormat {
    // Checked against the port's 0.0.4, whose data is Google's 9.0.39:
    // - Its parsing differs from libphonenumber's unless libphonenumberCompat is set. Without it,
    //   it read the 12 in "214-555-0142 (12)" as an extension, and wrote "ext. 12".
    // - Its first number builds its data in memory, which took 2 ms in a test on the iOS
    //   simulator, on the Mac's processor. An iPhone may take longer, so it runs off the main
    //   thread, as Android's does.
    // - PhoneNumberUtil is a global object that caches patterns in a HashMap without a lock, so
    //   portLock keeps Badge detail and a report from formatting at the same time.
    override suspend fun format(number: String): String = withContext(ioDispatcher) {
        portLock.withLock {
            formatOnlyPunctuation(number) {
                try {
                    PhoneNumberUtil.formatInOriginalFormat(
                        PhoneNumberUtil.parseAndKeepRawInput(it, "US", libphonenumberCompat = true),
                        regionCallingFrom = "US"
                    )
                } catch (_: PhoneNumberUtil.NumberParseException) {
                    null
                }
            }
        }
    }
}

private val portLock = Mutex()
