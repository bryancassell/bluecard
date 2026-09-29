package io.github.bryancassell.bluecard.ui

import android.content.res.Configuration
import android.icu.text.PluralRules
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.R
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
        lateinit var text: String
        composeTestRule.setContent {
            val arabicDigits = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag("en-US-u-nu-arab"))
            }
            CompositionLocalProvider(LocalConfiguration provides arabicDigits) {
                ProvideStringsLanguageResources {
                    text = stringResource(R.string.requirement_choice, 2, 3)
                }
            }
        }

        assertEquals("Do ٢ of ٣", text)
    }

    @Config(qualifiers = "fa")
    @Test
    fun stringsLocale_isStringsLanguage_notDevice() {
        lateinit var locale: Locale
        composeTestRule.setContent { locale = stringsLocale() }

        assertEquals(Locale.forLanguageTag("en"), locale)
    }
}
