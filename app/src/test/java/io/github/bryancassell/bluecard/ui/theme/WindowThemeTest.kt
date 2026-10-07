package io.github.bryancassell.bluecard.ui.theme

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.R as SplashScreenR
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Checks the window themes that show before and behind Compose. They have BlueCardTheme's
 * background, and system bar icons that show on it, in light and dark mode, so no other color
 * shows before the first frame.
 */
@RunWith(AndroidJUnit4::class)
class WindowThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val lightBackground = BlueCardLightColorScheme.background.toArgb()
    private val darkBackground = BlueCardDarkColorScheme.background.toArgb()

    private fun <T> resolve(theme: Int, attribute: Int, read: (TypedArray) -> T): T {
        val values = context.resources.newTheme()
            .apply { applyStyle(theme, true) }
            .obtainStyledAttributes(intArrayOf(attribute))
        try {
            return read(values)
        } finally {
            values.recycle()
        }
    }

    private fun color(theme: Int, attribute: Int) =
        resolve(theme, attribute) { it.getColor(0, Color.TRANSPARENT) }

    // Null if the theme doesn't set the attribute.
    private fun flag(theme: Int, attribute: Int) =
        resolve(theme, attribute) { if (it.hasValue(0)) it.getBoolean(0, false) else null }

    private fun assertWindowHasBackground(background: Int) {
        assertEquals(background, color(R.style.Theme_BlueCard, android.R.attr.windowBackground))
        // Android shows colorBackground behind the app, such as while resizing it in split-screen.
        assertEquals(background, color(R.style.Theme_BlueCard, android.R.attr.colorBackground))
    }

    // Android 8 to 11 draw core-splashscreen's splash screen, from its attribute.
    private fun splashScreenBackground() =
        color(R.style.Theme_BlueCard_Starting, SplashScreenR.attr.windowSplashScreenBackground)

    // On Android 8 to 11 the splash screen stays up in a window drawn from its theme until the
    // profile loads. The window reads a "light" bar, one with dark icons, as false if unset.
    private fun assertSplashScreenHasDarkSystemBarIcons(darkIcons: Boolean) {
        assertEquals(
            darkIcons,
            flag(R.style.Theme_BlueCard_Starting, android.R.attr.windowLightStatusBar) ?: false
        )
        assertEquals(
            darkIcons,
            flag(R.style.Theme_BlueCard_Starting, android.R.attr.windowLightNavigationBar) ?: false
        )
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_window_hasLightSchemeBackground() {
        assertWindowHasBackground(lightBackground)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_window_hasDarkSchemeBackground() {
        assertWindowHasBackground(darkBackground)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_splashScreen_hasLightSchemeBackground() {
        assertEquals(lightBackground, splashScreenBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_splashScreen_hasDarkSchemeBackground() {
        assertEquals(darkBackground, splashScreenBackground())
    }

    // Android 12+ draws the splash screen itself, from the platform's attribute.
    @Test
    @Config(sdk = [31], qualifiers = "notnight")
    fun android12AndLater_lightMode_splashScreen_hasLightSchemeBackground() {
        assertEquals(
            lightBackground,
            color(R.style.Theme_BlueCard_Starting, android.R.attr.windowSplashScreenBackground)
        )
    }

    @Test
    @Config(sdk = [31], qualifiers = "night")
    fun android12AndLater_darkMode_splashScreen_hasDarkSchemeBackground() {
        assertEquals(
            darkBackground,
            color(R.style.Theme_BlueCard_Starting, android.R.attr.windowSplashScreenBackground)
        )
    }

    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun android11_lightMode_splashScreen_hasDarkSystemBarIcons() {
        assertSplashScreenHasDarkSystemBarIcons(true)
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun android11_darkMode_splashScreen_hasLightSystemBarIcons() {
        assertSplashScreenHasDarkSystemBarIcons(false)
    }

    // The window theme follows dark mode as BlueCardTheme does, so Android's own popups, such as
    // the text selection toolbar, match the app. In dark mode it must say it isn't light: the
    // window reads an unset isLightTheme as true, and Android 10+ then darkens a "light" app
    // itself, with force dark or force invert (see ViewRootImpl.determineForceDarkType).
    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_window_isDeclaredLight() {
        assertEquals(true, flag(R.style.Theme_BlueCard, android.R.attr.isLightTheme))
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_window_isNotDeclaredLight() {
        assertEquals(false, flag(R.style.Theme_BlueCard, android.R.attr.isLightTheme))
    }
}
