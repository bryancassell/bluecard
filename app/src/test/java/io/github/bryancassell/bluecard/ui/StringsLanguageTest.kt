package io.github.bryancassell.bluecard.ui

import android.content.res.Resources
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

    private fun stringsLanguageResources(): Resources {
        lateinit var resources: Resources
        composeTestRule.setContent { resources = rememberStringsLanguageResources() }
        return resources
    }

    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_numbersUseStringsLanguageDigits() {
        val resources = stringsLanguageResources()

        assertEquals("Do 2 of 3", resources.getString(R.string.requirement_choice, 2, 3))
        assertEquals(
            "1 of 3 completed",
            resources.getQuantityString(R.plurals.home_eagle_completed, 1, 1, 3)
        )
    }

    @Config(qualifiers = "ar-rEG")
    @Test
    fun onArabicDeviceInEgypt_numbersUseStringsLanguageDigits() {
        val resources = stringsLanguageResources()

        assertEquals("Do 2 of 3", resources.getString(R.string.requirement_choice, 2, 3))
    }

    // Android picks a plural's form by the rules of the first locale in the resources'
    // configuration. Persian rules would pick the "one" form for 0 ("0 badge").
    @Config(qualifiers = "fa")
    @Test
    fun onPersianDevice_pluralsUseStringsLanguageRules() {
        val resources = stringsLanguageResources()

        assertEquals(Locale.forLanguageTag("en"), resources.configuration.locales[0])
    }

    @Config(qualifiers = "fa")
    @Test
    fun stringsLocale_isStringsLanguage_notDevice() {
        lateinit var locale: Locale
        composeTestRule.setContent { locale = stringsLocale() }

        assertEquals(Locale.forLanguageTag("en"), locale)
    }
}
