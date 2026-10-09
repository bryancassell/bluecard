package io.github.bryancassell.bluecard.ui

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.MainActivityUiState
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import org.junit.Assert.assertEquals
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
        assertBandCoversTopBar(WindowInsets.Type.statusBars())
    }

    // As the Scaffold's top bar, the band sets where pages start, so it must cover a window's
    // caption bar too, as in desktop windowing.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [36])
    @Test
    fun captionBar_isCoveredByTheBandToo() {
        assertBandCoversTopBar(WindowInsets.Type.captionBar())
    }

    /**
     * Shows a 24dp system bar of [type] across the top of the window, and checks that the band,
     * which pages start below, covers exactly it. Only Robolectric's native graphics draw real
     * pixels. On SDK 36, as the screenshot tests are: on SDK 37, Robolectric 4.17 leaves a class's
     * later captures blank.
     */
    private fun assertBandCoversTopBar(type: Int) {
        lateinit var view: View
        composeTestRule.setContent {
            view = LocalView.current
            BlueCardApp(MainActivityUiState.LoadFailed, onDismissDamagedProgressNotice = {})
        }
        val barHeight = with(composeTestRule.density) { 24.dp.roundToPx() }
        composeTestRule.runOnUiThread {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(type, Insets.of(0, barHeight, 0, 0))
                    .setVisible(type, true)
                    .build()
            )
        }

        // The left edge is clear of the message, so it shows what's behind it.
        val pixels = composeTestRule.onRoot().captureToImage().toPixelMap()
        assertEquals(BlueCardLightColorScheme.surfaceContainer, pixels[0, 0])
        assertEquals(BlueCardLightColorScheme.surfaceContainer, pixels[0, barHeight - 1])
        assertEquals(BlueCardLightColorScheme.background, pixels[0, barHeight])
    }
}
