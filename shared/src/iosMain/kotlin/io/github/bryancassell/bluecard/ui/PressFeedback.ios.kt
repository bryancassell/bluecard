@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
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

// iOS's press feedback is in common code, where Android's Compose UI tests check it. This file
// reads UIKit's color for it and provides it.

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

/** A row's press feedback in light or dark mode, in UIKit's color. */
internal fun uiKitRowHighlight(darkTheme: Boolean): PressHighlight =
    rowPressHighlight(rowHighlightColor(darkTheme), darkTheme)

@Composable
internal actual fun ProvidePressFeedback(darkTheme: Boolean, content: @Composable () -> Unit) =
    ProvideIosRowHighlight(remember(darkTheme) { uiKitRowHighlight(darkTheme) }, content)

@Composable
internal actual fun ButtonPressFeedback(
    filled: Boolean,
    content: @Composable (interactionSource: MutableInteractionSource?, modifier: Modifier) -> Unit
) = IosButtonPressFeedback(filled, content)

@Composable
internal actual fun MaterialPressFeedback(content: @Composable () -> Unit) =
    IosMaterialPressFeedback(content)
