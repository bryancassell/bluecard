package io.github.bryancassell.bluecard.ui

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Android keeps Material's checkboxes and radio buttons. */
internal actual val usesIosToggles: Boolean = false

/** Material's switch, which Compose draws over whatever is behind it, so needs no background. */
@Composable
internal actual fun PlatformSwitch(checked: Boolean, background: Color, modifier: Modifier) =
    Switch(checked = checked, onCheckedChange = null, modifier = modifier)
