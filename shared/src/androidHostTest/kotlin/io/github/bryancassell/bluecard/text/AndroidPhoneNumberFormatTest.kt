package io.github.bryancassell.bluecard.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric, for Android's PhoneNumberUtils.
@RunWith(AndroidJUnit4::class)
class AndroidPhoneNumberFormatTest : PhoneNumberFormatContract() {
    override val phoneNumberFormat = AndroidPhoneNumberFormat()

    // By the UK's rules, it would read 55 5123 4567.
    @Config(qualifiers = "en-rGB")
    @Test
    fun withoutACountryCode_onAPhoneSetToTheUk_followsUsRules() {
        assertEquals("GB", Locale.getDefault().country)

        assertEquals("(555) 123-4567", phoneNumberFormat.format("5551234567"))
    }
}
