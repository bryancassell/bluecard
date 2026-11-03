package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable

// Compose Multiplatform 1.12.2 has no imeAnimationTarget on iOS, so it can't tell, and the page
// asks to keep its fields in view on each frame as the keyboard moves. Short fields and Save stay
// above it (checked on the simulator). A field that stops fitting partway through the keyboard's
// opening can be left with its cursor behind it, as KeepInViewWhileFocused's comment explains;
// #352 checks text editing on iOS.
@Composable
internal actual fun isKeyboardMoving(): Boolean = false
