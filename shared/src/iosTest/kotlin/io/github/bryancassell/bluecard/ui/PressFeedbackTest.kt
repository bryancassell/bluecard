@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIBackgroundConfiguration
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UICellConfigurationState
import platform.UIKit.UIColor
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.resolvedColorWithTraitCollection
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks PressFeedback.ios.kt's values against UIKit's. */
class PressFeedbackTest {
    private fun traits(darkTheme: Boolean) = UITraitCollection.traitCollectionWithUserInterfaceStyle(
        if (darkTheme) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
    )

    private fun UIColor.toColor(darkTheme: Boolean): Color = memScoped {
        val red = alloc<CGFloatVar>()
        val green = alloc<CGFloatVar>()
        val blue = alloc<CGFloatVar>()
        val alpha = alloc<CGFloatVar>()
        resolvedColorWithTraitCollection(traits(darkTheme))
            .getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr)
        Color(red.value.toFloat(), green.value.toFloat(), blue.value.toFloat(), alpha.value.toFloat())
    }

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

        assertEquals(FILLED_BUTTON_PRESSED_ALPHA, background.toColor(darkTheme = false).alpha, 0.001f)
    }

    @Test
    fun aPress_fadesOverIosDefaultAnimationDuration() {
        assertEquals(CATransaction.animationDuration() * 1000, PRESS_FADE_MILLIS.toDouble(), 0.5)
    }

    @Test
    fun aRow_isHighlightedAsAListCell() {
        for (darkTheme in listOf(false, true)) {
            val state = UICellConfigurationState(traitCollection = traits(darkTheme))
            state.setHighlighted(true)
            val highlighted = UIBackgroundConfiguration.listPlainCellConfiguration()
                .updatedConfigurationForState(state)
                .resolvedBackgroundColorForTintColor(UIColor.blackColor)

            assertEquals(highlighted.toColor(darkTheme), rowHighlightColor(darkTheme), "dark: $darkTheme")
        }
    }
}
