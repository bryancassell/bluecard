package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A message shown in place of a screen's content, such as when its data couldn't be loaded.
 * Screen readers don't announce it yet when it appears (#69).
 */
@Composable
fun ScreenMessage(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier.padding(16.dp))
}
