@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

/** UIKit's traits for light or dark mode. */
internal fun interfaceStyle(darkTheme: Boolean): UITraitCollection =
    UITraitCollection.traitCollectionWithUserInterfaceStyle(
        if (darkTheme) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
    )

/** This color as it shows with [traits]. */
internal fun UIColor.toComposeColor(traits: UITraitCollection): Color = memScoped {
    val red = alloc<CGFloatVar>()
    val green = alloc<CGFloatVar>()
    val blue = alloc<CGFloatVar>()
    val alpha = alloc<CGFloatVar>()
    resolvedColorWithTraitCollection(traits).getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr)
    Color(red.value.toFloat(), green.value.toFloat(), blue.value.toFloat(), alpha.value.toFloat())
}

/** The background UIKit gives a highlighted list row, `systemGray4`, in light or dark mode. */
internal fun rowHighlightColor(darkTheme: Boolean): Color =
    UIColor.systemGray4Color.toComposeColor(interfaceStyle(darkTheme))

@Composable
internal actual fun ProvidePressFeedback(darkTheme: Boolean, content: @Composable () -> Unit) {
    val highlight =
        remember(darkTheme) { rowPressHighlight(rowHighlightColor(darkTheme), darkTheme) }
    CompositionLocalProvider(LocalIndication provides highlight, content = content)
}

private val ButtonDim = buttonPressDim(filled = false)
private val FilledButtonDim = buttonPressDim(filled = true)

// Material's buttons always draw a ripple, which only LocalRippleConfiguration changes. Google
// supports that for a component, not across the app, so it's provided around each one.
private val FocusAndHoverRipple = RippleConfiguration(rippleAlpha = FocusAndHoverRippleAlpha)

@Composable
internal actual fun ButtonPressFeedback(
    filled: Boolean,
    content: @Composable (interactionSource: MutableInteractionSource?, modifier: Modifier) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dim = if (filled) FilledButtonDim else ButtonDim
    CompositionLocalProvider(LocalRippleConfiguration provides FocusAndHoverRipple) {
        content(interactionSource, Modifier.indication(interactionSource, dim))
    }
}

// The date picker's controls are Material's own, out of reach of the dim and highlight, so they
// show only focus and hover. A day shows its selection at once. The month arrows, the mode toggle
// and the year menu show nothing while held, though each acts as soon as it's let go, until #352
// revisits the date picker, by agreement.
@Composable
internal actual fun MaterialPressFeedback(content: @Composable () -> Unit) =
    CompositionLocalProvider(
        LocalRippleConfiguration provides FocusAndHoverRipple,
        content = content
    )
