package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText

/** A polite live region, which screen readers announce when it changes. */
val isPoliteLiveRegion =
    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

private val isHidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

/**
 * Runs [show], which brings up [message], and checks that screen readers announce it: it's in a
 * polite live region that was already composed, hidden while it was empty, which Compose announces
 * when its text changes, unlike a new one (see `ScreenMessage`).
 */
fun SemanticsNodeInteractionsProvider.assertAnnouncedWhenShown(message: String, show: () -> Unit) {
    onNodeWithText(message).assertDoesNotExist()
    val hiddenBefore = onAllNodes(isPoliteLiveRegion and isHidden).fetchSemanticsNodes()
        .map { it.id }
    val wasHiddenBefore = SemanticsMatcher("was hidden in its place before it was shown") {
        it.id in hiddenBefore
    }
    show()
    onNodeWithText(message).assert(isPoliteLiveRegion and !isHidden).assert(wasHiddenBefore)
}
