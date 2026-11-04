package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UISwitch

/** Apple describes checkboxes and radio buttons only for macOS. */
internal actual val usesIosToggles: Boolean = true

/**
 * UIKit's own switch, which UIKit draws in each iOS version's look, at its own size. With no
 * interaction mode, it takes no touches and VoiceOver skips it (`InteropWrappingView`, Compose UI
 * 1.12.2), so the row it's in takes the taps and is read as the switch. It slides to a new state,
 * but shows the first one at once.
 */
@Composable
internal actual fun PlatformSwitch(checked: Boolean, modifier: Modifier) = UIKitView(
    factory = { UISwitch().apply { on = checked } },
    modifier = modifier,
    update = { if (it.on != checked) it.setOn(checked, animated = true) },
    properties = UIKitInteropProperties(interactionMode = null)
)
