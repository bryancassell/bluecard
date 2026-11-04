package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android keeps Material's checkboxes and radio buttons. */
internal actual val usesIosToggles: Boolean = false

@Composable
internal actual fun PlatformSwitch(checked: Boolean, modifier: Modifier) =
    Switch(checked = checked, onCheckedChange = null, modifier = modifier)
