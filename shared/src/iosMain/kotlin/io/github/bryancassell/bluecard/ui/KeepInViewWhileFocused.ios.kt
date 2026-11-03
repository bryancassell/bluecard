package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable

// Compose Multiplatform 1.12.2 has no imeAnimationTarget on iOS, so it can't tell. So the page
// asks to keep its fields in view again on each frame as the keyboard moves, and the request once
// it stops is the one that holds. #352 checks text editing on iOS.
@Composable
internal actual fun isKeyboardMoving(): Boolean = false
