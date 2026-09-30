package io.github.bryancassell.bluecard.ui.theme

import android.content.Context
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
 * Checks that the splash screen and the window behind Compose have BlueCardTheme's background in
 * light and dark mode, so no other color shows before the first frame.
 */
@RunWith(AndroidJUnit4::class)
class WindowThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val blueCardBackground = BlueCardColorScheme.background.toArgb()

    private fun color(theme: Int, attribute: Int): Int {
        val values = context.resources.newTheme()
            .apply { applyStyle(theme, true) }
            .obtainStyledAttributes(intArrayOf(attribute))
        try {
            return values.getColor(0, Color.TRANSPARENT)
        } finally {
            values.recycle()
        }
    }

    private fun windowBackground() = color(R.style.Theme_BlueCard, android.R.attr.windowBackground)

    private fun splashScreenBackground() =
        color(R.style.Theme_BlueCard_Starting, SplashScreenR.attr.windowSplashScreenBackground)

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_windowBackground_isBlueCardBackground() {
        assertEquals(blueCardBackground, windowBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_windowBackground_isBlueCardBackground() {
        assertEquals(blueCardBackground, windowBackground())
    }

    @Test
    @Config(qualifiers = "notnight")
    fun lightMode_splashScreenBackground_isBlueCardBackground() {
        assertEquals(blueCardBackground, splashScreenBackground())
    }

    @Test
    @Config(qualifiers = "night")
    fun darkMode_splashScreenBackground_isBlueCardBackground() {
        assertEquals(blueCardBackground, splashScreenBackground())
    }
}
