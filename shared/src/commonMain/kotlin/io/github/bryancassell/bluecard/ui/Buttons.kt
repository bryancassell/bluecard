package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Material's buttons, with the platform's press feedback (ButtonPressFeedback): Material's ripple
// on Android, and on iOS a dim, as UIKit's buttons have. Each takes only the parameters the app
// uses.

/** Material's filled [Button]. */
@Composable
fun BlueCardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    ButtonPressFeedback(filled = true) { interactionSource, feedback ->
        Button(
            onClick = onClick,
            modifier = modifier.then(feedback),
            enabled = enabled,
            interactionSource = interactionSource,
            content = content
        )
    }
}

/** Material's [FilledTonalButton]. */
@Composable
fun BlueCardFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    ButtonPressFeedback(filled = true) { interactionSource, feedback ->
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.then(feedback),
            enabled = enabled,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
}

/** Material's [OutlinedButton]. */
@Composable
fun BlueCardOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    ButtonPressFeedback(filled = false) { interactionSource, feedback ->
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.then(feedback),
            enabled = enabled,
            colors = colors,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
}

/** Material's [TextButton]. */
@Composable
fun BlueCardTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    content: @Composable RowScope.() -> Unit
) {
    ButtonPressFeedback(filled = false) { interactionSource, feedback ->
        TextButton(
            onClick = onClick,
            modifier = modifier.then(feedback),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content
        )
    }
}

/** Material's [IconButton]. */
@Composable
fun BlueCardIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    ButtonPressFeedback(filled = false) { interactionSource, feedback ->
        IconButton(
            onClick = onClick,
            modifier = modifier.then(feedback),
            enabled = enabled,
            interactionSource = interactionSource,
            content = content
        )
    }
}
