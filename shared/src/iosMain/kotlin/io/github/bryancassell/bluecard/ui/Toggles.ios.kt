@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * UIKit's own switch, which UIKit draws in each iOS version's look. With no interaction mode, it
 * takes no touches and VoiceOver skips it (`InteropWrappingView`, Compose UI 1.12.2), so the row
 * it's in takes the taps and is read as the switch. It slides to a new state, but shows the first
 * one at once.
 *
 * A UIKit view is under Compose's canvas, which is cleared over it, so what Compose draws behind it
 * doesn't show, and what Compose draws over it covers it, as ToggleRow's grey while held did. So
 * the switch is drawn on the page's background, the color behind ToggleRow, where the root view's
 * white or black showed around its rounded ends. An overlay (`placedAsOverlay`) isn't clipped
 * (`UIKitInteropElementHolder`, Compose UI 1.12.2), so it would draw over the top bar as the page
 * scrolls under it.
 */
@Composable
internal actual fun PlatformSwitch(checked: Boolean, modifier: Modifier) {
    val background = MaterialTheme.colorScheme.background.toUIColor()
    // UIKit's own size. Compose fitted it to its Auto Layout size, 61 by 28 points on iOS 27.0,
    // which squeezed its track 2 points narrower than iOS's own switches.
    val size = remember { UISwitch().bounds.useContents { DpSize(size.width.dp, size.height.dp) } }
    UIKitView(
        factory = { UISwitch().apply { on = checked } },
        modifier = modifier.size(size),
        update = {
            it.backgroundColor = background
            if (it.on != checked) it.setOn(checked, animated = true)
        },
        properties = UIKitInteropProperties(interactionMode = null)
    )
}
