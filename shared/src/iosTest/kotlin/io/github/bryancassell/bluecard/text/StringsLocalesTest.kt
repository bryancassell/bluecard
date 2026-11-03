package io.github.bryancassell.bluecard.text

import androidx.compose.ui.text.intl.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.calendarIdentifier
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages
import platform.Foundation.scriptCode

class StringsLocalesTest {
    // What an earlier run or test saved, so the simulator's languages are its own.
    @BeforeTest
    @AfterTest
    fun removeSavedLanguages() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
    }

    // French rather than the strings' English, so the test doesn't pass on an English simulator
    // alone.
    @Test
    fun useStringsLanguageByDefault_makesItTheLanguageComposeReads() {
        useStringsLanguageByDefault { "fr" }

        assertEquals("fr", Locale.current.language)
    }

    // As the app's own dates are, which Material 3's date picker reads it for.
    @Test
    fun useStringsLanguageByDefault_savesItInTheDevicesRegion_inTheGregorianCalendar() {
        useStringsLanguageByDefault { "fr" }

        val saved = NSLocale(NSLocale.preferredLanguages.first() as String)
        assertEquals(currentRegion(), saved.countryCode)
        assertEquals(NSCalendarIdentifierGregorian, saved.calendarIdentifier)
    }

    @Test
    fun useStringsLanguageByDefault_keepsTheDevicesLanguagesAfterIt() {
        val device = NSLocale.preferredLanguages

        useStringsLanguageByDefault { "fr" }

        val first = gregorianLanguageTag(stringsLocale("fr", currentRegion()))
        assertEquals(
            listOf<Any?>(first) + device.filter {
                it != first
            },
            NSLocale.preferredLanguages
        )
    }

    // Iran's calendar is the Persian one.
    @Test
    fun gregorianLanguageTag_isTheLocaleInTheGregorianCalendar() {
        val tag = gregorianLanguageTag(NSLocale("en_IR"))

        assertEquals("en-IR-u-ca-gregory", tag)
        assertEquals(NSCalendarIdentifierGregorian, NSLocale(tag).calendarIdentifier)
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

    // Traditional Chinese, which in another region would be Simplified.
    @Test
    fun stringsLocale_ofATagWithItsOwnRegion_keepsIt() {
        assertEquals("TW", stringsLocale("zh-TW", "US").countryCode)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun deviceRegions_givesTheRegion_andTheNewOneWhenTheLocaleChanges() = runTest {
        var region = "US"
        val regions = mutableListOf<String?>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            deviceRegions { region }.toList(regions)
        }

        region = "FR"
        NSNotificationCenter.defaultCenter.postNotificationName(
            NSCurrentLocaleDidChangeNotification,
            null
        )
        collecting.cancel()

        assertEquals(listOf<String?>("US", "FR"), regions)
    }
}
