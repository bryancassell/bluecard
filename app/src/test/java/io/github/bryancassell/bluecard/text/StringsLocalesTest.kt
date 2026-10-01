package io.github.bryancassell.bluecard.text

import android.content.Context
import android.os.LocaleList
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun stringsLocaleOutsideCompose_isStringsLanguage_notDevice() {
        val locale = stringsLocale(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(Locale.forLanguageTag("en"), locale)
    }

    // For code outside Compose, such as the PDF report.
    @Config(qualifiers = "fa")
    @Test
    fun stringsLanguageResources_onPersianDevice_areInStringsLanguage_leftToRight() {
        val resources = stringsLanguageResources(ApplicationProvider.getApplicationContext())

        assertEquals("Do 2 of 3", resources.getString(R.string.requirement_choice, 2, 3))
        assertEquals(Locale.forLanguageTag("en"), resources.configuration.locales[0])
        assertEquals(View.LAYOUT_DIRECTION_LTR, resources.configuration.layoutDirection)
    }

    @Test
    fun stringsLanguageResources_onDeviceInStringsLanguage_areTheDevices() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertSame(context.resources, stringsLanguageResources(context))
    }
}
