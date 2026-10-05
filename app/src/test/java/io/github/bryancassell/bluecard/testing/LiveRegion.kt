package io.github.bryancassell.bluecard.testing

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithText

private val isPoliteLiveRegion =
    SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

/**
 * Runs [show], which brings up [message], and checks that screen readers announce it: it's in a
 * polite live region that was already composed, which Compose announces when its text changes,
 * unlike a new one (see `ScreenMessage`).
 */
fun SemanticsNodeInteractionsProvider.assertAnnouncedWhenShown(message: String, show: () -> Unit) {
    val composedBefore = onAllNodes(isPoliteLiveRegion).fetchSemanticsNodes().map { it.id }
    show()
    onNodeWithText(message)
        .assert(isPoliteLiveRegion)
        .assert(SemanticsMatcher("was composed before it was shown") { it.id in composedBefore })
}
