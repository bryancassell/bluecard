package io.github.bryancassell.bluecard.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric, for Android's PhoneNumberUtils.
@RunWith(AndroidJUnit4::class)
class AndroidPhoneNumberFormatTest : PhoneNumberFormatContract() {
    override val phoneNumberFormat = AndroidPhoneNumberFormat(UnconfinedTestDispatcher())

    // By the UK's rules, it would read 55 5123 4567.
    @Config(qualifiers = "en-rGB")
    @Test
    fun withoutACountryCode_onAPhoneSetToTheUk_followsUsRules() = runTest {
        assertEquals("GB", Locale.getDefault().country)

        assertEquals("(555) 123-4567", phoneNumberFormat.format("5551234567"))
    }

    // Its first number reads from disk, which on the main thread held Badge detail back.
    @Test
    fun format_runsOnTheIoDispatcher() = runTest {
        var dispatched = false
        val io = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatched = true
                block.run()
            }
        }

        AndroidPhoneNumberFormat(io).format("5551234567")

        assertTrue(dispatched)
    }
}
