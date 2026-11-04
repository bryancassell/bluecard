package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.delay

/**
 * Whether the double-tap timeout since this first composed hasn't ended, for a dialog that ignores
 * taps as it opens: the second tap of a double tap that opened it would otherwise land on it.
 */
@Composable
fun isOpeningForDoubleTap(): Boolean {
    var opening by remember { mutableStateOf(true) }
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    LaunchedEffect(timeoutMillis) {
        delay(timeoutMillis)
        opening = false
    }
    return opening
}
