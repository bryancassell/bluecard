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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A message shown in place of a screen's content, such as when its data couldn't be loaded, or
 * nothing while [text] is null.
 *
 * Screen readers announce the message as it appears, because it's a polite live region whose
 * text changes. Without that, TalkBack read the message only when its focus happened to move onto
 * it, so with its focus on a heading that stays, as on Badges, nothing told the scout (#69).
 * TalkBack may read it a second time as its focus moves off something that went away, such as
 * the loading indicator. A pane title was tried first: TalkBack treated the message like a window
 * and said "BlueCard" whenever it went away.
 *
 * Compose announces a live region only when a node it has already seen changes, not when a new
 * one appears (`sendSemanticsPropertyChangeEvents` in
 * `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI 1.12.1). So compose it while the
 * screen loads too, with no text, from the same call, as [LoadingOrMessage] does. A failure after
 * the screen has loaded replaces its content, so that message is a new node and isn't announced;
 * that's rare. Nor is a message the screen is composed with, as after rotation: it was announced
 * when it first appeared. Compose also reports a node's first layout as a change to it, which
 * TalkBack reads in a live region (see `MatchCount` in BadgesScreen.kt), so these two cases are
 * being checked again in #281.
 *
 * A live region is what Android points to: when Android 16 deprecated `announceForAccessibility`,
 * its behavior changes (https://developer.android.com/about/versions/16/behavior-changes-all)
 * pointed to live regions "to inform the user of changes to critical UI", and to pane titles "for
 * significant UI changes like window changes". Besides TalkBack's "BlueCard", the pane title made
 * Compose throw when it had to merge the title into a parent, which happens only with a screen
 * reader on.
 *
 * While it has no text, it's hidden from screen readers, which would otherwise stop on it when
 * swiping: Compose lets them focus any node with text, even empty text. A hidden node is still
 * tracked, so the message replacing it is announced.
 */
@Composable
fun ScreenMessage(text: String?, modifier: Modifier = Modifier) {
    Text(
        text = text.orEmpty(),
        modifier = modifier
            .padding(16.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text == null) hideFromAccessibility()
            }
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
