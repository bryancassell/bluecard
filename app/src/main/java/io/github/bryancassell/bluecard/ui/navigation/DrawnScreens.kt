package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey

/**
 * The screens NavDisplay is drawing, including one still animating out after it left the back
 * stack, for NavDisplay's entryDecorators to keep up to date with [decorator].
 *
 * NavDisplay keeps a screen's state, ViewModel included, until the screen is out of both the
 * back stack and composition. So a screen opened again while it's still animating out comes
 * back as it was: a Tracker entry page that has closed after a save, say, rather than a new
 * one.
 */
class DrawnScreens {
    // By content key, as NavDisplay keeps each screen's state.
    private val contentKeys = mutableSetOf<Any>()

    /** Whether [entry]'s screen is being drawn. */
    operator fun contains(entry: NavEntry<*>): Boolean = entry.contentKey in contentKeys

    val decorator = NavEntryDecorator<NavKey>(decorate = { entry ->
        DisposableEffect(entry.contentKey) {
            contentKeys += entry.contentKey
            onDispose { contentKeys -= entry.contentKey }
        }
        entry.Content()
    })
}
