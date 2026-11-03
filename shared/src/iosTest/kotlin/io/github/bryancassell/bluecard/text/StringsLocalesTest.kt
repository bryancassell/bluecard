package io.github.bryancassell.bluecard.text

import androidx.compose.ui.text.intl.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.Foundation.scriptCode

class StringsLocalesTest {
    // What a test saved, so the simulator's languages are its own again for the next.
    @AfterTest
    fun removeSavedLanguage() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
    }

    // French rather than the strings' English, so the test doesn't pass on an English simulator
    // alone.
    @Test
    fun useStringsLanguageByDefault_makesItTheLanguageComposeReads() {
        useStringsLanguageByDefault { "fr" }

        assertEquals(listOf<Any?>("fr"), NSLocale.preferredLanguages)
        assertEquals("fr", Locale.current.language)
    }

    // So the strings are picked by the device's languages, not by what the last launch saved.
    @Test
    fun useStringsLanguageByDefault_readsTheStringsLanguageInTheDevicesLanguages() {
        val device = NSLocale.preferredLanguages
        useStringsLanguageByDefault { "fr" }

        var readIn: List<*>? = null
        useStringsLanguageByDefault {
            readIn = NSLocale.preferredLanguages
            "fr"
        }

        assertEquals(device, readIn)
    }

    @Test
    fun stringsLocale_isTheStringsLanguageInTheDevicesRegion() {
        assertEquals("en_FR", stringsLocale("en", "FR").localeIdentifier)
    }

    @Test
    fun stringsLocale_withNoRegion_isTheStringsLanguage() {
        assertEquals("en", stringsLocale("en", null).localeIdentifier)
    }

    // Its identifier is written sr-Latn_RS, so its parts are compared.
    @Test
    fun stringsLocale_keepsTheStringsScript() {
        val locale = stringsLocale("sr-Latn", "RS")

        assertEquals("sr", locale.languageCode)
        assertEquals("Latn", locale.scriptCode)
        assertEquals("RS", locale.countryCode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deviceRegions_givesTheRegion_andAgainWhenTheLocaleChanges() = runTest {
        val region = NSLocale.currentLocale.countryCode
        val regions = mutableListOf<String?>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            deviceRegions().toList(regions)
        }

        NSNotificationCenter.defaultCenter.postNotificationName(
            NSCurrentLocaleDidChangeNotification,
            null
        )
        collecting.cancel()

        assertEquals(listOf(region, region), regions)
    }
}
