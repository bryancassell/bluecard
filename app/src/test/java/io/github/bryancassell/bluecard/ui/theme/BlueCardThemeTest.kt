package io.github.bryancassell.bluecard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Checks that BlueCardTheme uses the app's own colors on every Android version, in light and
 * dark mode, rather than the wallpaper's or Material's defaults.
 */
@RunWith(AndroidJUnit4::class)
class BlueCardThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun colorScheme(): ColorScheme {
        lateinit var colorScheme: ColorScheme
        composeTestRule.setContent {
            BlueCardTheme {
                colorScheme = MaterialTheme.colorScheme
            }
        }
        return colorScheme
    }

    // Dynamic color would give the wallpaper's primary on Android 12+, and the default schemes
    // Material's purple, so primary and background tell the schemes apart.
    private fun assertBlueCardColors(colorScheme: ColorScheme) {
        assertEquals(BlueCardColorScheme.primary, colorScheme.primary)
        assertEquals(BlueCardColorScheme.background, colorScheme.background)
    }

    @Test
    fun primary_isScoutingAmericaBlue() {
        assertEquals(Color(0xFF003F87), colorScheme().primary)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun android12AndLater_lightMode_usesBlueCardColors() {
        assertBlueCardColors(colorScheme())
    }

    @Test
    @Config(qualifiers = "night")
    fun android12AndLater_darkMode_usesBlueCardColors() {
        assertBlueCardColors(colorScheme())
    }

    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun android11_lightMode_usesBlueCardColors() {
        assertBlueCardColors(colorScheme())
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun android11_darkMode_usesBlueCardColors() {
        assertBlueCardColors(colorScheme())
    }
}
