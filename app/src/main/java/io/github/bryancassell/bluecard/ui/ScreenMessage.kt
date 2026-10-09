package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Screen readers announce the message as it appears, because it's a polite live region. Without
 * that, TalkBack read the message only when its focus happened to move onto it, so with its focus
 * on a heading that stays, as on Badges, nothing told the scout (#69). It's a live region only for
 * a message other than the one it showed last ([rememberIsNewText]), so it isn't read out again
 * after the phone rotates. TalkBack may read it a second time as its focus moves off something that
 * went away, such as the loading indicator. A pane title was tried first: TalkBack treated the
 * message like a window and said "BlueCard" whenever it went away.
 *
 * A live region is what Android points to: when Android 16 deprecated `announceForAccessibility`,
 * its behavior changes (https://developer.android.com/about/versions/16/behavior-changes-all)
 * pointed to live regions "to inform the user of changes to critical UI", and to pane titles "for
 * significant UI changes like window changes". Besides TalkBack's "BlueCard", the pane title made
 * Compose throw when it had to merge the title into a parent, which happens only with a screen
 * reader on.
 */
@Composable
fun ScreenMessage(text: String?, modifier: Modifier = Modifier) {
    AnnouncedText(text, modifier.padding(16.dp))
}

/**
 * [text], which screen readers announce as it appears, but not again after the phone rotates: a
 * polite live region while [rememberIsNewText] says the text is new. Nothing while [text] is
 * null.
 *
 * While it has no text, it's hidden from screen readers, which would otherwise stop on it when
 * swiping: Compose lets them focus any node with text, even empty text.
 */
@Composable
fun AnnouncedText(text: String?, modifier: Modifier = Modifier) {
    val isNew = rememberIsNewText(text)
    Text(
        text = text.orEmpty(),
        modifier = modifier.semantics {
            if (isNew) liveRegion = LiveRegionMode.Polite
            if (text == null) hideFromAccessibility()
        }
    )
}

/**
 * The loading indicator while [message] is null, then [message] in its place. A screen shows its
 * loading, load-failed and unavailable states with one call, from one `when` branch. That was so
 * screen readers would announce the message from a node Compose had already seen, but a new one
 * is announced too (see [rememberIsNewText]).
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

/**
 * Whether [text] is new where it's shown, so a live region showing it should be read out: it
 * isn't null, and this place didn't show it last, even before the phone rotated.
 *
 * Compose reports a node's first layout, and each later change to its size or position, as a
 * change to it (`onLayoutChange` in `AndroidComposeViewAccessibilityDelegateCompat`, Compose UI
 * 1.12.1), and TalkBack reads a live region on any change it's the source of. So a live region
 * composed already showing its text, as after the phone rotates, was read out again. On Badges
 * that held back the screen's heading by about 3 seconds (#281). A screen composed showing text
 * it hadn't shown, such as a failure while the scout was on a later page, is still read out.
 *
 * It's worked out once per text, so a live region stays one for as long as its text, however late
 * TalkBack looks at it.
 */
@Composable
fun rememberIsNewText(text: String?): Boolean {
    val shown = rememberSaveable(saver = ShownText.Saver) { ShownText() }
    val isNew = remember(text) { text != null && text != shown.text }
    // Not state: it's read only when the text changes, so updating it needn't recompose.
    SideEffect { shown.text = text }
    return isNew
}

/** The [text] a place showed last, saved with the screen. */
private class ShownText(var text: String? = null) {
    companion object {
        val Saver = Saver<ShownText, String>(save = { it.text }, restore = { ShownText(it) })
    }
}
