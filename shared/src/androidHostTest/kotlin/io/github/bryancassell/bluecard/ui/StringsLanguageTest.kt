package io.github.bryancassell.bluecard.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.badges_count
import io.github.bryancassell.bluecard.resources.home_eagle_completed
import io.github.bryancassell.bluecard.resources.requirement_choice
import io.github.bryancassell.bluecard.text.useStringsLocalesByDefault
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app has only English strings. On a Persian device, or an Arabic one in some regions,
 * the device's language would pick the strings' plural forms by its own rules ("0 merit badge"),
 * and format dates and lists in it.
 */
@RunWith(AndroidJUnit4::class)
class StringsLanguageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** What [text] returns with the strings' locales the default, as MainActivity makes them. */
    private fun inStringsLanguage(text: @Composable () -> String): String {
        runBlocking { useStringsLocalesByDefault(ApplicationProvider.getApplicationContext()) }
        lateinit var result: String
        composeTestRule.setContent { result = text() }
        return result
    }

    /** What [text] returns with [LocalLocaleFormats] provided on [Device] [locales]. */
    private fun onDevice(locales: String, text: @Composable () -> String): String {
        lateinit var result: String
        composeTestRule.setContent {
            Device(locales) {
                CompositionLocalProvider(LocalLocaleFormats provides rememberLocaleFormats()) {
                    result = text()
                }
            }
        }
        return result
    }

    /**
     * Stands in for a device whose languages are [locales], BCP 47 tags in order, since
     * Robolectric's qualifiers take one locale.
     */
    @Composable
    private fun Device(locales: String, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val device = remember(locales) {
            val deviceConfiguration = Configuration(configuration).apply {
                setLocales(LocaleList.forLanguageTags(locales))
            }
            context.createConfigurationContext(deviceConfiguration)
        }
        CompositionLocalProvider(
            LocalContext provides device,
            LocalConfiguration provides device.resources.configuration,
            content = content
        )
    }

    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_numbersUseLatinDigits() {
        assertEquals(
            "Do 2 of 3",
            inStringsLanguage { stringResource(Res.string.requirement_choice, 2, 3) }
        )
    }

    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_pluralsUseLatinDigits() {
        assertEquals(
            "1 of 3 completed",
            inStringsLanguage { pluralStringResource(Res.plurals.home_eagle_completed, 1, 1, 3) }
        )
    }

    @Config(qualifiers = "ar-rEG")
    @Test
    fun onArabicDeviceInEgypt_numbersUseLatinDigits() {
        assertEquals(
            "Do 2 of 3",
            inStringsLanguage { stringResource(Res.string.requirement_choice, 2, 3) }
        )
    }

    // Compose Multiplatform picks a plural's form by the default locale's rules. English rules
    // pick "other" for 0 ("0 merit badges"); Persian rules would pick "one" ("0 merit badge").
    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_pluralFormsUseStringsLanguageRules() {
        assertEquals(
            "0 merit badges",
            inStringsLanguage { pluralStringResource(Res.plurals.badges_count, 0, 0) }
        )
    }

    // A locale can choose other digits, as English with Arabic digits does. "%1$d" in a string
    // inserts Latin digits, so dates use them too, and a sentence doesn't mix two kinds.
    @Test
    fun onDeviceInStringsLanguageWithOtherDigits_usesTheLanguagesDigits() {
        val text = onDevice("en-US-u-nu-arab") {
            val date = LocalLocaleFormats.current.formatDate(LocalDate(2026, 9, 29))
            "${stringResource(Res.string.requirement_choice, 2, 3)}; $date"
        }

        assertEquals("Do 2 of 3; Sep 29, 2026", text)
    }

    // British English lists have no comma before "and". Numbers still use English digits.
    @Test
    fun onDeviceWithStringsLanguageSecond_usesThatLocale() {
        val text = onDevice("fa-IR,en-GB") {
            val badges =
                LocalLocaleFormats.current.formatList(listOf("Cycling", "Hiking", "Swimming"))
            "${stringResource(Res.string.requirement_choice, 2, 3)}; $badges"
        }

        assertEquals("Do 2 of 3; Cycling, Hiking and Swimming", text)
    }

    @Config(qualifiers = "fa")
    @Test
    fun stringsLocale_isStringsLanguage_notDevice() {
        lateinit var locale: Locale
        composeTestRule.setContent { locale = stringsLocale() }

        assertEquals(Locale.forLanguageTag("en"), locale)
    }
}
