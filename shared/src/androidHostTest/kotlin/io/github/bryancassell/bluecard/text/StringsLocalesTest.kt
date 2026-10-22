package io.github.bryancassell.bluecard.text

import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.badges_count
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The strings' locales outside Compose. ui/StringsLanguageTest checks what Compose code sees.
 */
@RunWith(AndroidJUnit4::class)
class StringsLocalesTest {
    @Test
    fun stringsLocales_deviceHasStringsLanguage_putsItsLocaleFirst() {
        assertEquals(
            LocaleList.forLanguageTags("en-GB,fa-IR"),
            stringsLocales(LocaleList.forLanguageTags("fa-IR,en-GB"), Locale.forLanguageTag("en"))
        )
    }

    @Test
    fun stringsLocales_deviceLacksStringsLanguage_putsStringsLanguageFirst() {
        assertEquals(
            LocaleList.forLanguageTags("en,fa-IR"),
            stringsLocales(LocaleList.forLanguageTags("fa-IR"), Locale.forLanguageTag("en"))
        )
    }

    // "%1$d" in a string inserts Latin digits, so dates and totals don't use other ones.
    @Test
    fun stringsLocales_deviceChoosesOtherDigits_leavesThemOut() {
        assertEquals(
            LocaleList.forLanguageTags("en-GB,fa-IR"),
            stringsLocales(
                LocaleList.forLanguageTags("en-GB-u-nu-arab,fa-IR"),
                Locale.forLanguageTag("en")
            )
        )
    }

    // Without its digits, the first is the device's second, which a LocaleList can't repeat:
    // Android 11 throws for it.
    @Config(sdk = [30])
    @Test
    fun stringsLocales_firstWithoutItsDigitsIsAnotherOfTheDevices_listsItOnce() {
        assertEquals(
            LocaleList.forLanguageTags("en-US,fa-IR"),
            stringsLocales(
                LocaleList.forLanguageTags("en-US-u-nu-arab,en-US,fa-IR"),
                Locale.forLanguageTag("en")
            )
        )
    }

    // Serbian is written in Cyrillic or Latin script; "sr-RS" is Cyrillic.
    @Test
    fun stringsLocales_matchesScript() {
        assertEquals(
            LocaleList.forLanguageTags("sr-Latn-RS,sr-RS"),
            stringsLocales(
                LocaleList.forLanguageTags("sr-RS,sr-Latn-RS"),
                Locale.forLanguageTag("sr-Latn")
            )
        )
    }

    @Config(qualifiers = "fa")
    @Test
    fun stringsLocaleOutsideCompose_isStringsLanguage_notDevice() = runTest {
        val locale = stringsLocale(ApplicationProvider.getApplicationContext())

        assertEquals(Locale.forLanguageTag("en"), locale)
    }

    // Compose Multiplatform's strings, and Compose's own Locale.current, read the default.
    @Config(qualifiers = "fa-rIR")
    @Test
    fun useStringsLocalesByDefault_onPersianDevice_putsStringsLanguageFirst() = runTest {
        val locale = useStringsLocalesByDefault(ApplicationProvider.getApplicationContext())

        assertEquals(Locale.forLanguageTag("en"), locale)
        assertEquals(LocaleList.forLanguageTags("en,fa-IR"), LocaleList.getDefault())
    }

    // For code outside Compose, such as the PDF report. Persian rules would pick "one" for 0.
    @Config(qualifiers = "fa")
    @Test
    fun useStringsLocalesByDefault_onPersianDevice_pluralsOutsideComposeUseStringsLanguageRules() =
        runTest {
            useStringsLocalesByDefault(ApplicationProvider.getApplicationContext())

            assertEquals("0 merit badges", getPluralString(Res.plurals.badges_count, 0, 0))
        }

    @Test
    fun useStringsLocalesByDefault_onDeviceInStringsLanguage_keepsDevicesLocales() = runTest {
        val device = LocaleList.getDefault()

        useStringsLocalesByDefault(ApplicationProvider.getApplicationContext())

        assertEquals(device, LocaleList.getDefault())
    }
}
