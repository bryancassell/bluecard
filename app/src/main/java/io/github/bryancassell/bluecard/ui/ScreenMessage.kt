package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A message shown in place of a screen's content, such as when its data couldn't be loaded, or
 * nothing while [text] is null.
 *
 * Screen readers announce the message as it appears, because it's a polite live region whose
 * text changes. Compose announces a live region only when a node it has already seen changes, not
 * when a new one appears (`sendSemanticsPropertyChangeEvents` in
 * `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1). So compose it while the
 * screen loads too, with no text, from the same call, as [LoadingOrMessage] does.
 */
@Composable
fun ScreenMessage(text: String?, modifier: Modifier = Modifier) {
    Text(
        text = text.orEmpty(),
        modifier = modifier
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/**
 * The loading indicator while [message] is null, then [message] in its place. A screen shows its
 * loading, load-failed and unavailable states with one call, from one `when` branch, so screen
 * readers announce the message (see [ScreenMessage]).
 */
@Composable
fun LoadingOrMessage(message: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        if (message == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        ScreenMessage(message)
    }
}
