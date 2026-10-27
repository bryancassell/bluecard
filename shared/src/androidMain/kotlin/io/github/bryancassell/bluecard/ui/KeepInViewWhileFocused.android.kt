package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

// imeAnimationTarget is experimental, and the only way to tell the keyboard is still moving. A
// change to it would fail the build when Compose is updated.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun isKeyboardMoving(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.ime.getBottom(density) != WindowInsets.imeAnimationTarget.getBottom(density)
}
