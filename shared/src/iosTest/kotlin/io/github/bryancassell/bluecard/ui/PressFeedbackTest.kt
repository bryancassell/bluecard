@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.graphics.BlendMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIBackgroundConfiguration
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UICellConfigurationState
import platform.UIKit.UIColor
import platform.UIKit.UIControlStateNormal

/** Checks the press feedback's values against UIKit's. */
class PressFeedbackTest {
    @Test
    fun aButton_dimsAsASystemButtonsTitleDoes() {
        val button = UIButton.buttonWithType(UIButtonTypeSystem)
        button.setTitle("Save", forState = UIControlStateNormal)
        button.setFrame(CGRectMake(0.0, 0.0, 100.0, 44.0))

        button.setHighlighted(true)
        button.layoutIfNeeded()

        assertEquals(BUTTON_PRESSED_ALPHA, button.titleLabel!!.alpha.toFloat(), 0.001f)
    }

    @Test
    fun aFilledButton_dimsAsAFilledButtonsBackgroundDoes() {
        val button = UIButton.buttonWithConfiguration(
            UIButtonConfiguration.filledButtonConfiguration(),
            primaryAction = null
        )

        button.setHighlighted(true)
        val background = button.configuration!!.updatedConfigurationForButton(button).background
            .resolvedBackgroundColorForTintColor(button.tintColor)

        assertEquals(
            FILLED_BUTTON_PRESSED_ALPHA,
            background.toComposeColor(interfaceStyle(darkTheme = false)).alpha,
            0.001f
        )
    }

    @Test
    fun aPress_fadesOverIosDefaultAnimationDuration() {
        assertEquals(CATransaction.animationDuration() * 1000, PRESS_FADE_MILLIS.toDouble(), 0.5)
    }

    @Test
    fun aRowsHighlight_isMultipliedInLightModeAndScreenedInDarkMode() {
        assertEquals(
            PressHighlight(rowHighlightColor(darkTheme = false), BlendMode.Multiply, 250),
            uiKitRowHighlight(darkTheme = false)
        )
        assertEquals(
            PressHighlight(rowHighlightColor(darkTheme = true), BlendMode.Screen, 250),
            uiKitRowHighlight(darkTheme = true)
        )
    }

    @Test
    fun aRow_isHighlightedAsAListCell() {
        for (darkTheme in listOf(false, true)) {
            val traits = interfaceStyle(darkTheme)
            val state = UICellConfigurationState(traitCollection = traits)
            state.setHighlighted(true)
            val highlighted = UIBackgroundConfiguration.listPlainCellConfiguration()
                .updatedConfigurationForState(state)
                .resolvedBackgroundColorForTintColor(UIColor.blackColor)

            assertEquals(
                highlighted.toComposeColor(traits),
                rowHighlightColor(darkTheme),
                "dark: $darkTheme"
            )
        }
    }
}
