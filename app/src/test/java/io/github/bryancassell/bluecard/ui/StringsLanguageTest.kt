package io.github.bryancassell.bluecard.ui

import android.content.res.Configuration
import android.icu.text.PluralRules
import android.os.LocaleList
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.R
import io.github.bryancassell.bluecard.ui.badges.rememberBadgeNameListFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    /** What [text] returns inside [ProvideStringsLanguageResources] on [Device] [locales]. */
    private fun onDevice(locales: String, text: @Composable () -> String): String {
        lateinit var result: String
        composeTestRule.setContent {
            Device(locales) { ProvideStringsLanguageResources { result = text() } }
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

    // English strings are laid out left-to-right, as on an English phone, so rows and
    // sentences read as English: a sentence's final period stays at its end.
    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_laysOutInStringsLanguageDirection() {
        lateinit var device: LayoutDirection
        lateinit var provided: LayoutDirection
        composeTestRule.setContent {
            device = LocalLayoutDirection.current
            ProvideStringsLanguageResources { provided = LocalLayoutDirection.current }
        }

        assertEquals(LayoutDirection.Rtl, device)
        assertEquals(LayoutDirection.Ltr, provided)
    }

    // Resources with a direction-specific version (such as drawable-ldrtl) must follow the
    // layout's direction.
    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_resourcesTakeStringsLanguageDirection() {
        val direction = inStringsLanguage {
            LocalResources.current.configuration.layoutDirection.toString()
        }

        assertEquals(View.LAYOUT_DIRECTION_LTR.toString(), direction)
    }

    @Test
    fun onDeviceInStringsLanguage_usesDeviceResources() {
        lateinit var provided: Any
        lateinit var device: Any
        composeTestRule.setContent {
            device = LocalResources.current
            ProvideStringsLanguageResources { provided = LocalResources.current }
        }

        assertSame(device, provided)
    }

    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_providesOtherResources() {
        lateinit var provided: Any
        lateinit var device: Any
        composeTestRule.setContent {
            device = LocalResources.current
            ProvideStringsLanguageResources { provided = LocalResources.current }
        }

        assertNotSame(device, provided)
    }

    // Changing the phone's language recreates the activity. The content must be at the same
    // place in the composition whether or not the new language needs other resources, so its
    // saved state, such as the back stack, comes back.
    @Test
    fun deviceLanguageChange_keepsContentState() {
        var locales by mutableStateOf("en-US")
        var state: MutableList<String>? = null
        composeTestRule.setContent {
            Device(locales) {
                ProvideStringsLanguageResources {
                    state = rememberSaveable { mutableListOf<String>() }
                }
            }
        }
        composeTestRule.runOnIdle { state!!.add("kept") }

        locales = "fa-IR"
        composeTestRule.waitForIdle()

        assertEquals(listOf("kept"), state)
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

    @Config(qualifiers = "fa")
    @Test
    fun stringsLocale_isStringsLanguage_notDevice() {
        lateinit var locale: Locale
        composeTestRule.setContent { locale = stringsLocale() }

        assertEquals(Locale.forLanguageTag("en"), locale)
    }
}
