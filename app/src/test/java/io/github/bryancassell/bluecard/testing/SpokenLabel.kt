package io.github.bryancassell.bluecard.testing

import android.view.View
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * What TalkBack reads as [node], found in the unmerged tree, when none of its parts is a stop of
 * its own: its own description or text, then each part's in turn, since a description doesn't
 * replace its parts' (#166). It reads from [view]'s `AccessibilityNodeProvider`, so it leaves out
 * the parts Compose doesn't give TalkBack, and those it marks as not visible to the user, such as
 * parts scrolled off screen (#305). Parts come in the semantics tree's order, where TalkBack sorts
 * them by position. Turn on a screen reader first ([turnOnScreenReader]), so Compose answers as it
 * does for TalkBack.
 */
private fun spokenLabel(view: View, node: SemanticsNode): String {
    val provider = view.accessibilityNodeProvider
    fun labels(node: SemanticsNode): List<CharSequence> {
        val info = provider.createAccessibilityNodeInfo(node.id)
        if (info == null || !info.isVisibleToUser) return emptyList()
        return listOfNotNull(info.contentDescription ?: info.text) +
            node.children.flatMap(::labels)
    }
    return labels(node).joinToString(". ")
}

/**
 * Scrolls the page until only the last 40dp of what [matcher] finds in the unmerged tree shows,
 * with its [firstLine] fully off screen and its [lastLine] still on it, and returns what TalkBack
 * reads as it ([spokenLabel]). TalkBack can focus something like that, as when a page comes back
 * scrolled down, and doesn't scroll it into view first (#305). The page needs room to scroll
 * that far, below what [matcher] finds.
 */
fun ComposeTestRule.readWithOnlyItsLastLineShown(
    view: View,
    matcher: SemanticsMatcher,
    firstLine: String,
    lastLine: String
): String {
    // The page, not a text field, which can scroll too.
    val page = onNode(hasScrollAction() and hasAnyDescendant(matcher), useUnmergedTree = true)
    val pageTop = page.getUnclippedBoundsInRoot().top
    val node = onNode(matcher, useUnmergedTree = true)
    val scrollBy = with(density) {
        (node.getUnclippedBoundsInRoot().bottom - pageTop - 40.dp).toPx()
    }
    page.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scrollBy) }
    fun line(text: String) =
        onNode(hasText(text) and hasAnyAncestor(matcher), useUnmergedTree = true)
    assertTrue(line(firstLine).getUnclippedBoundsInRoot().bottom < pageTop)
    assertTrue(line(lastLine).getUnclippedBoundsInRoot().bottom > pageTop)
    return spokenLabel(view, node.fetchSemanticsNode())
}

/**
 * Whether Compose gives TalkBack [node] as something to read, from [view]'s
 * `AccessibilityNodeProvider`. Turn on a screen reader first ([turnOnScreenReader]).
 */
fun isGivenToScreenReaders(view: View, node: SemanticsNode): Boolean =
    view.accessibilityNodeProvider.createAccessibilityNodeInfo(node.id)?.isVisibleToUser == true
