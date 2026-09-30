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
 * background and dark system bar icons in light and dark mode, so no other color shows before
 * the first frame, and Android doesn't darken the app in dark mode.
 */
@RunWith(AndroidJUnit4::class)
class WindowThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val blueCardBackground = BlueCardColorScheme.background.toArgb()

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

    private fun assertWindowHasBlueCardBackground() {
        assertEquals(
            blueCardBackground,
            color(R.style.Theme_BlueCard, android.R.attr.windowBackground)
        )
        // Android shows colorBackground behind the app, such as while resizing it in split-screen.
        assertEquals(
            blueCardBackground,
            color(R.style.Theme_BlueCard, android.R.attr.colorBackground)
        )
    }

    // Android 8 to 11 draw core-splashscreen's splash screen, from its attribute.
    private fun splashScreenBackground() =
        color(R.style.Theme_BlueCard_Starting, SplashScreenR.attr.windowSplashScreenBackground)

    // On Android 8 to 11 the splash screen stays up in a window drawn from its theme until the
    // profile loads. The theme's parent is dark in dark mode and sets neither attribute.
    private fun assertSplashScreenHasDarkSystemBarIcons() {
        assertEquals(
            true,
            flag(R.style.Theme_BlueCard_Starting, android.R.attr.windowLightStatusBar)
        )
        assertEquals(
            true,
            flag(R.style.Theme_BlueCard_Starting, android.R.attr.windowLightNavigationBar)
        )
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_window_hasBlueCardBackground() {
        assertWindowHasBlueCardBackground()
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_window_hasBlueCardBackground() {
        assertWindowHasBlueCardBackground()
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_splashScreen_hasBlueCardBackground() {
        assertEquals(blueCardBackground, splashScreenBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_splashScreen_hasBlueCardBackground() {
        assertEquals(blueCardBackground, splashScreenBackground())
    }

    // Android 12+ draws the splash screen itself, from the platform's attribute.
    @Test
    @Config(sdk = [31], qualifiers = "night")
    fun android12AndLater_darkMode_splashScreen_hasBlueCardBackground() {
        assertEquals(
            blueCardBackground,
            color(R.style.Theme_BlueCard_Starting, android.R.attr.windowSplashScreenBackground)
        )
    }

    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun android11_lightMode_splashScreen_hasDarkSystemBarIcons() {
        assertSplashScreenHasDarkSystemBarIcons()
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun android11_darkMode_splashScreen_hasDarkSystemBarIcons() {
        assertSplashScreenHasDarkSystemBarIcons()
    }

    // Android's force dark and force invert skip a theme that isn't declared light (see
    // ViewRootImpl.determineForceDarkType).
    @Test
    @Config(qualifiers = "night")
    fun darkMode_window_isNotDeclaredLight() {
        assertEquals(false, flag(R.style.Theme_BlueCard, android.R.attr.isLightTheme))
    }

    // So Android's own popups, such as the text selection toolbar, are light.
    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_window_isDeclaredLight() {
        assertEquals(true, flag(R.style.Theme_BlueCard, android.R.attr.isLightTheme))
    }
}
