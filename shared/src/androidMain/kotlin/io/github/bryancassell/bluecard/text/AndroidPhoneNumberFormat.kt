package io.github.bryancassell.bluecard.text

import android.telephony.PhoneNumberUtils

/**
 * [PhoneNumberFormat] with Android's [PhoneNumberUtils]. Its first number, and the first from
 * each other country, reads that country's data from disk: on a Pixel 10 emulator the US's took
 * 0.4 s in a newly started app.
 */
class AndroidPhoneNumberFormat : PhoneNumberFormat {
    // PhoneNumberUtils formats with libphonenumber's formatInOriginalFormat, which keeps a number
    // typed with + in international form and returns null for one it can't read. It skips text
    // before the number and some after it, so it would drop a label such as "Cell: ". And with no
    // international format for 7 US digits, +1 555-0100 comes back as +1 5550100. Both are left
    // as typed. Checked on Android 8 and 17, which give the same results.
    override fun format(number: String): String {
        if (!number.all { it in '0'..'9' || it in "+-.()/" || it.isWhitespace() }) return number
        val formatted = PhoneNumberUtils.formatNumber(number, "US") ?: return number
        return formatted.takeIf { digitGroups(it) >= digitGroups(number) } ?: number
    }
}

private val DIGITS = Regex("[0-9]+")

private fun digitGroups(text: String) = DIGITS.findAll(text).count()
