@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGFloatVar
import platform.UIKit.UIColor
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.resolvedColorWithTraitCollection
import platform.UIKit.systemGray4Color

// UIKit's own values, read from UIKit on the iOS 27.0 simulator. PressFeedbackTest checks them
// against UIKit, so a change in iOS fails it.

/** A system button's title while pressed (`UIButton` of type `.system`). */
internal const val BUTTON_PRESSED_ALPHA = 0.2f

/**
 * A filled button's background while pressed (`UIButton.Configuration.filled()`). Here it dims
 * the whole button, label too, which differs little at this alpha and needs no access to the
 * button's colors.
 */
internal const val FILLED_BUTTON_PRESSED_ALPHA = 0.75f

/** How long a press takes to fade once let go: iOS's default animation duration. */
internal const val PRESS_FADE_MILLIS = 250

/** The background UIKit gives a highlighted list row, `systemGray4`, in light or dark mode. */
internal fun rowHighlightColor(darkTheme: Boolean): Color = memScoped {
    val style = if (darkTheme) {
        UIUserInterfaceStyle.UIUserInterfaceStyleDark
    } else {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }
    val color = UIColor.systemGray4Color.resolvedColorWithTraitCollection(
        UITraitCollection.traitCollectionWithUserInterfaceStyle(style)
    )
    val red = alloc<CGFloatVar>()
    val green = alloc<CGFloatVar>()
    val blue = alloc<CGFloatVar>()
    val alpha = alloc<CGFloatVar>()
    color.getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr)
    Color(
        red.value.toFloat(),
        green.value.toFloat(),
        blue.value.toFloat(),
        alpha.value.toFloat()
    )
}

/**
 * UIKit replaces a highlighted row's background with [rowHighlightColor]. A row here draws its own
 * background, such as `ListItem`'s, so the highlight is blended over it instead: multiplied in
 * light mode and screened in dark mode. That gives exactly UIKit's color over the white and black
 * of UIKit's rows, darkens or lightens other backgrounds in proportion, and keeps text readable.
 */
@Composable
internal actual fun ProvidePressFeedback(darkTheme: Boolean, content: @Composable () -> Unit) {
    val highlight = remember(darkTheme) {
        PressHighlight(
            color = rowHighlightColor(darkTheme),
            blendMode = if (darkTheme) BlendMode.Screen else BlendMode.Multiply,
            fadeMillis = PRESS_FADE_MILLIS
        )
    }
    CompositionLocalProvider(LocalIndication provides highlight, content = content)
}

private val ButtonDim = PressDim(BUTTON_PRESSED_ALPHA, PRESS_FADE_MILLIS)
private val FilledButtonDim = PressDim(FILLED_BUTTON_PRESSED_ALPHA, PRESS_FADE_MILLIS)

// Material's buttons always draw a ripple, which only LocalRippleConfiguration turns off. Google
// supports that for a component, not across the app, so it's provided around each one.
@Composable
internal actual fun ButtonPressFeedback(
    filled: Boolean,
    content: @Composable (interactionSource: MutableInteractionSource?, modifier: Modifier) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dim = if (filled) FilledButtonDim else ButtonDim
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        content(interactionSource, Modifier.indication(interactionSource, dim))
    }
}

// The ripple is turned off for the date picker as for a button. Its selection, which shows at
// once, is its feedback.
@Composable
internal actual fun MaterialPressFeedback(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
