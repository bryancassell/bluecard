package io.github.bryancassell.bluecard.text

import android.telephony.PhoneNumberUtils
import io.github.bryancassell.bluecard.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * [PhoneNumberFormat] with Android's [PhoneNumberUtils], on [ioDispatcher]. Its first number,
 * and the first from each other country, reads that country's data from disk: on a Pixel 10
 * emulator, the US's took 0.4 s in a newly started app.
 */
class AndroidPhoneNumberFormat(@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher) :
    PhoneNumberFormat {
    // PhoneNumberUtils formats with libphonenumber's formatInOriginalFormat, and returns null for
    // a number it can't read. Checked on Android 8 and 17, which give the same results.
    override suspend fun format(number: String): String = withContext(ioDispatcher) {
        formatOnlyPunctuation(number) { PhoneNumberUtils.formatNumber(it, "US") }
    }
}
