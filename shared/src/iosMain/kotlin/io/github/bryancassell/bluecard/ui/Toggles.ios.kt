@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UISwitch

/** Apple describes checkboxes and radio buttons only for macOS. */
internal actual val usesIosToggles: Boolean = true

/**
 * UIKit's own size for a switch, which is the same for every switch. Compose fitted it to its Auto
 * Layout size, 61 by 28 points on iOS 27.0, which squeezed its track 2 points narrower than iOS's
 * own switches.
 */
private val UIKitSwitchSize: DpSize by lazy {
    UISwitch().bounds.useContents { DpSize(size.width.dp, size.height.dp) }
}

/**
 * UIKit's own switch, which UIKit draws in each iOS version's look. With no interaction mode, it
 * takes no touches and VoiceOver skips it (`InteropWrappingView`, Compose UI 1.12.2), so the row
 * it's in takes the taps and is read as the switch. It slides to a new state, but shows the first
 * one at once.
 *
 * A UIKit view is under Compose's canvas, which is cleared over it, so what Compose draws behind it
 * doesn't show, and what Compose draws over it covers it, as ToggleRow's grey while held did. So
 * the switch is drawn on [background], where the root view's white or black showed around its
 * rounded ends. What it draws outside its frame, such as a shadow, is hidden too. That was checked
 * only on iOS 27, since Xcode 27.1 offers no simulator for iOS 17 or 18, whose switch looks
 * different. An overlay (`placedAsOverlay`) isn't clipped (`UIKitInteropElementHolder`, Compose UI
 * 1.12.2), so it would draw over the top bar as the page scrolls under it.
 */
@Composable
internal actual fun PlatformSwitch(checked: Boolean, background: Color, modifier: Modifier) {
    val backgroundColor = remember(background) { background.toUIColor() }
    UIKitView(
        factory = { UISwitch().apply { on = checked } },
        modifier = modifier.size(UIKitSwitchSize),
        update = {
            it.backgroundColor = backgroundColor
            if (it.on != checked) it.setOn(checked, animated = true)
        },
        properties = UIKitInteropProperties(interactionMode = null)
    )
}
