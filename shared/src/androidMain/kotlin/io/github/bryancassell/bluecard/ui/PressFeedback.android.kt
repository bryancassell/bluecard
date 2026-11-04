package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Android keeps Material's ripple, which the theme and Material's components already draw.

@Composable
internal actual fun ProvidePressFeedback(darkTheme: Boolean, content: @Composable () -> Unit) =
    content()

@Composable
internal actual fun ButtonPressFeedback(
    filled: Boolean,
    content: @Composable (interactionSource: MutableInteractionSource?, modifier: Modifier) -> Unit
) = content(null, Modifier)
