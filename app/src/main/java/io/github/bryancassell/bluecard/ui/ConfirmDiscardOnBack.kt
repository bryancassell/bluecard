package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R

/**
 * Whether a page has changes that aren't saved, for Back, which the navigation root handles.
 * The page keeps [changed] up to date with [ConfirmDiscardOnBack], and the root asks it to
 * confirm discarding them ([askToDiscard]) rather than closing it.
 */
class UnsavedChanges {
    /**
     * Whether the page's fields differ from what's saved, as it last showed. It's kept while
     * another page covers it, so Back decides from it before the page is shown again.
     */
    var changed = false
        set(value) {
            // Saved while it asked, as when a save finishes with the dialog open: nothing is
            // left to discard.
            if (field && !value) confirming = false
            field = value
        }

    /** Back arrived while there were changes, so the page asks before discarding them. */
    var confirming by mutableStateOf(false)

    /** Back on the page: asks to discard its changes, if it has any. Returns whether it asked. */
    fun askToDiscard(): Boolean {
        if (changed) confirming = true
        return changed
    }
}

/** The page's [UnsavedChanges], which the navigation root provides to each page. */
val LocalUnsavedChanges = staticCompositionLocalOf<UnsavedChanges> {
    error("Pages are shown by BlueCardNavDisplay, which provides their UnsavedChanges.")
}

/**
 * Asks before Back discards the page's unsaved changes. The page calls it whatever it shows,
 * with whether its fields have [changed], so a page that stops showing them, such as when it
 * can't load, closes on Back. With changes, Back opens a [ConfirmDialog] with [message], whose
 * red Discard button calls [onDiscard] to close the page. It asks even when Save is off, as with
 * a blank name or every field of a saved tracker row emptied. Back discarded edits with no
 * warning before (#99). Saving on Back instead would close the page before a failed save could
 * be reported, and would leave no way to throw an edit away.
 */
@Composable
fun ConfirmDiscardOnBack(
    changed: Boolean,
    onDiscard: () -> Unit,
    message: String = stringResource(R.string.discard_changes_message)
) {
    val unsaved = LocalUnsavedChanges.current
    // Back reads it when it arrives, so it follows what the page shows.
    SideEffect { unsaved.changed = changed }
    if (unsaved.confirming && changed) {
        ConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            message = message,
            confirmLabel = stringResource(R.string.discard_changes_confirm),
            onConfirm = {
                unsaved.confirming = false
                onDiscard()
            },
            onDismiss = { unsaved.confirming = false }
        )
    }
}
