package io.github.bryancassell.bluecard.ui

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.MainActivityUiState
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.ui.theme.BlueCardDarkColorScheme
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Loading and LoadFailed states, and the status bar's background, which every state shows.
 * MainActivityTest covers Ready, which needs Hilt for the screens.
 */
@RunWith(AndroidJUnit4::class)
class BlueCardAppTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loading_showsNeitherOnboardingNorHome() {
        composeTestRule.setContent {
            BlueCardApp(MainActivityUiState.Loading, onDismissDamagedProgressNotice = {})
        }

        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        // Home's button that opens the badge list, and its loading indicator.
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
        composeTestRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        ).assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        composeTestRule.setContent {
            BlueCardApp(MainActivityUiState.LoadFailed, onDismissDamagedProgressNotice = {})
        }

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to BlueCard").assertDoesNotExist()
        composeTestRule.onNodeWithText("Merit badges").assertDoesNotExist()
    }

    @Test
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        var uiState by mutableStateOf<MainActivityUiState>(MainActivityUiState.Loading)
        composeTestRule.setContent {
            BlueCardApp(uiState, onDismissDamagedProgressNotice = {})
        }

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = MainActivityUiState.LoadFailed }
    }

    // A page scrolled up to the status bar ends at its edge. On the page's own color, its cut-off
    // text ran into the clock (#315).
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [36])
    @Test
    fun statusBar_isDrawnInAColorSetApartFromThePage() {
        assertBandCovers(WindowInsets.Type.statusBars())
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "night", sdk = [36])
    @Test
    fun statusBar_inDarkMode_isDrawnInTheDarkSchemesColors() {
        assertBandCovers(WindowInsets.Type.statusBars(), BlueCardDarkColorScheme)
    }

    // As the Scaffold's top bar, the band sets where pages start, so it must cover whatever the
    // Scaffold's own padding would keep pages out of: a window's caption bar in desktop windowing,
    // and a camera cutout taller than the status bar.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [36])
    @Test
    fun captionBar_isCoveredByTheBandToo() {
        assertBandCovers(WindowInsets.Type.captionBar())
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [36])
    @Test
    fun displayCutout_isCoveredByTheBandToo() {
        assertBandCovers(WindowInsets.Type.displayCutout())
    }

    /**
     * Gives the window a 24dp inset of [type] across its top, and checks that the band covers
     * exactly it in [scheme]'s colors, set apart from the page, and that the page starts below it.
     * Only Robolectric's native graphics draw real pixels. On SDK 36, as the screenshot tests are:
     * on SDK 37, Robolectric 4.17 leaves a class's later captures blank.
     */
    private fun assertBandCovers(type: Int, scheme: ColorScheme = BlueCardLightColorScheme) {
        lateinit var view: View
        composeTestRule.setContent {
            view = LocalView.current
            BlueCardApp(MainActivityUiState.LoadFailed, onDismissDamagedProgressNotice = {})
        }
        val inset = 24.dp
        val insetPx = with(composeTestRule.density) { inset.roundToPx() }
        composeTestRule.runOnUiThread {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(type, Insets.of(0, insetPx, 0, 0))
                    .setVisible(type, true)
                    .build()
            )
        }

        // The left edge is clear of the message, so it shows what's behind it.
        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()
        assertEquals(scheme.surfaceContainer, pixels[0, 0])
        assertEquals(scheme.surfaceContainer, pixels[0, insetPx - 1])
        assertEquals(scheme.background, pixels[0, insetPx])
        assertNotEquals(pixels[0, 0], pixels[0, insetPx])
        // ScreenMessage is inset 16dp from the top of the page.
        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        )
            .assertTopPositionInRootIsEqualTo(inset + 16.dp)
    }
}
