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
    // PhoneNumberUtils formats with libphonenumber's formatInOriginalFormat, which keeps a number
    // typed with + in international form and returns null for one it can't read. It skips text
    // before the number and some after it, so it would drop a label such as "Cell: ". And with no
    // international format for 7 US digits, +1 555-0100 comes back as +1 5550100. Both are left
    // as typed. Checked on Android 8 and 17, which give the same results.
    override suspend fun format(number: String): String = withContext(ioDispatcher) {
        if (!number.all { it in '0'..'9' || it in "+-.()/" || it.isWhitespace() }) {
            return@withContext number
        }
        val formatted = PhoneNumberUtils.formatNumber(number, "US") ?: return@withContext number
        if (digitBreaks(formatted).containsAll(digitBreaks(number))) formatted else number
    }
}

private val DIGITS = Regex("[0-9]+")

/**
 * Where [text]'s groups of digits end, but for the last, counted in digits: {3, 6} for
 * "555-123-4567". Formatting changes no digit, so a break typed and missing from the formatted
 * number means it joined digits typed apart.
 */
private fun digitBreaks(text: String): Set<Int> =
    DIGITS.findAll(text).map { it.value.length }.runningReduce(Int::plus).toList().dropLast(1)
        .toSet()
