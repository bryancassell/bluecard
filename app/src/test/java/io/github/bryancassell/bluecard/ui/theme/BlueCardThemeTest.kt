package io.github.bryancassell.bluecard.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks which color scheme BlueCardTheme picks for each combination of settings. */
@RunWith(AndroidJUnit4::class)
class BlueCardThemeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun colorSchemeFor(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
        lateinit var colorScheme: ColorScheme
        composeTestRule.setContent {
            BlueCardTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                colorScheme = MaterialTheme.colorScheme
            }
        }
        return colorScheme
    }

    @Test
    fun dynamicColor_light_usesWallpaperLightScheme() {
        assertEquals(
            dynamicLightColorScheme(context).primary,
            colorSchemeFor(darkTheme = false, dynamicColor = true).primary
        )
    }

    @Test
    fun dynamicColor_dark_usesWallpaperDarkScheme() {
        assertEquals(
            dynamicDarkColorScheme(context).primary,
            colorSchemeFor(darkTheme = true, dynamicColor = true).primary
        )
    }

    @Test
    fun noDynamicColor_light_usesDefaultLightScheme() {
        assertEquals(
            lightColorScheme().primary,
            colorSchemeFor(darkTheme = false, dynamicColor = false).primary
        )
    }

    @Test
    fun noDynamicColor_dark_usesDefaultDarkScheme() {
        assertEquals(
            darkColorScheme().primary,
            colorSchemeFor(darkTheme = true, dynamicColor = false).primary
        )
    }

    // Dynamic color needs Android 12 (API 31); older versions fall back to the default scheme.
    @Test
    @Config(sdk = [30])
    fun dynamicColor_beforeAndroid12_usesDefaultLightScheme() {
        assertEquals(
            lightColorScheme().primary,
            colorSchemeFor(darkTheme = false, dynamicColor = true).primary
        )
    }
}
