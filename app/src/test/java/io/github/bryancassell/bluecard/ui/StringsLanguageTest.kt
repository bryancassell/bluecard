package io.github.bryancassell.bluecard.ui

import android.content.Context
import android.content.res.Configuration
import android.icu.text.PluralRules
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app has only English strings. On a Persian device, or an Arabic one in some regions,
 * the device's language would format their numbers with its own digits ("Do ۲ of ۳").
 */
@RunWith(AndroidJUnit4::class)
class StringsLanguageTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** What [text] returns inside [ProvideStringsLanguageResources]. */
    private fun inStringsLanguage(text: @Composable () -> String): String {
        lateinit var result: String
        composeTestRule.setContent { ProvideStringsLanguageResources { result = text() } }
        return result
    }

    /**
     * What [text] returns inside [ProvideStringsLanguageResources] on a device whose languages
     * are [deviceLocales], as BCP 47 tags in order. Robolectric's qualifiers take one locale.
     */
    private fun onDevice(deviceLocales: String, text: @Composable () -> String): String {
        lateinit var result: String
        composeTestRule.setContent {
            val device = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList.forLanguageTags(deviceLocales))
            }
            CompositionLocalProvider(LocalConfiguration provides device) {
                ProvideStringsLanguageResources { result = text() }
            }
        }
        return result
    }

    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_numbersUseStringsLanguageDigits() {
        assertEquals(
            "Do 2 of 3",
            inStringsLanguage { stringResource(R.string.requirement_choice, 2, 3) }
        )
    }

    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_pluralsUseStringsLanguageDigits() {
        assertEquals(
            "1 of 3 completed",
            inStringsLanguage { pluralStringResource(R.plurals.home_eagle_completed, 1, 1, 3) }
        )
    }

    @Config(qualifiers = "ar-rEG")
    @Test
    fun onArabicDeviceInEgypt_numbersUseStringsLanguageDigits() {
        assertEquals(
            "Do 2 of 3",
            inStringsLanguage { stringResource(R.string.requirement_choice, 2, 3) }
        )
    }

    // Android picks a plural's form by the rules of the resources' first locale. English
    // rules pick "other" for 0 ("0 badges"); Persian rules would pick "one" ("0 badge").
    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_pluralFormsUseStringsLanguageRules() {
        val form = inStringsLanguage {
            PluralRules.forLocale(LocalResources.current.configuration.locales[0]).select(0.0)
        }

        assertEquals("other", form)
    }

    // Android offers a choice of digits for some languages, such as Persian with Western
    // digits. When the device's language is the strings' language, that choice is kept.
    @Test
    fun onDeviceInStringsLanguage_keepsItsDigitChoice() {
        val text = onDevice("en-US-u-nu-arab") {
            stringResource(R.string.requirement_choice, 2, 3)
        }

        assertEquals("Do ٢ of ٣", text)
    }

    // British English lists have no comma before "and". Numbers still use English digits.
    @Test
    fun onDeviceWithStringsLanguageSecond_usesThatLocale() {
        val text = onDevice("fa-IR,en-GB") {
            val badges = rememberBadgeNameListFormatter()
                .format(listOf("Cycling", "Hiking", "Swimming"))
            "${stringResource(R.string.requirement_choice, 2, 3)}; $badges"
        }

        assertEquals("Do 2 of 3; Cycling, Hiking and Swimming", text)
    }

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

    // A translation whose strings_language isn't a valid tag, such as "pt_BR", still loads:
    // Android falls back to the device's locales. The app has no translations yet, so this
    // reads a Compose UI string that has one.
    @Test
    fun stringsLocales_invalidStringsLanguage_fallsBackToDeviceLocales() {
        val locales = stringsLocales(
            LocaleList.forLanguageTags("pt-BR"),
            Locale.forLanguageTag("pt_BR")
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(locales)
        }
        val resources = context.createConfigurationContext(configuration).resources

        assertEquals("Desativado", resources.getString(androidx.compose.ui.R.string.state_off))
    }

    @Config(qualifiers = "fa")
    @Test
    fun stringsLocale_isStringsLanguage_notDevice() {
        lateinit var locale: Locale
        composeTestRule.setContent { locale = stringsLocale() }

        assertEquals(Locale.forLanguageTag("en"), locale)
    }
}
