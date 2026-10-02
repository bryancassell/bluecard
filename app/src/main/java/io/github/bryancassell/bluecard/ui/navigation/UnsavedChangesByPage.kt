package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import io.github.bryancassell.bluecard.ui.LocalUnsavedChanges
import io.github.bryancassell.bluecard.ui.UnsavedChanges

/**
 * Each page's [UnsavedChanges], kept while it's in the back stack, even while another page
 * covers it, for NavDisplay's entryDecorators to provide with [decorator].
 *
 * The navigation root decides what Back does from the back stack as it is when Back arrives
 * ([askToDiscard]). A back handler of the page's own couldn't: it's added and turned on only
 * as the page is drawn, a frame or more after the back stack changes. Back just after a page
 * opened another would then ask on the page sliding away, and the second of two quick Backs
 * would close a page with unsaved changes before it was drawn again.
 */
class UnsavedChangesByPage {
    // By content key, as NavDisplay keeps each screen's state.
    private val pages = mutableMapOf<Any, UnsavedChanges>()

    /**
     * Back on [entry]'s page: asks to discard its unsaved changes, if it has any. Returns
     * whether it asked, rather than the page closing.
     */
    fun askToDiscard(entry: NavEntry<*>): Boolean = pages[entry.contentKey]?.askToDiscard() == true

    val decorator = NavEntryDecorator<NavKey>(
        onPop = { contentKey -> pages.remove(contentKey) },
        decorate = { entry ->
            val unsaved = remember(entry.contentKey) {
                pages.getOrPut(entry.contentKey) { UnsavedChanges() }
            }
            CompositionLocalProvider(LocalUnsavedChanges provides unsaved) { entry.Content() }
        }
    )
}
