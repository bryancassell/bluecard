package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * The [platform]'s text toolbar, a text field's edit menu ("Select / Select All"), which a
 * [SystemAlert] keeps hidden while it shows ([hold]). `MainViewController` provides it as
 * `LocalTextToolbar` for the app's content.
 *
 * Hiding the menu once isn't enough. A text field shows it again each time the field moves while
 * the field's own toolbar state wants it, and only the field changes that state: a tap on it, a
 * text change, or losing focus. So the menu came back as the keyboard went down for an alert and
 * the field moved, over the alert's message. The field calls [showMenu] for each move, as
 * Compose Multiplatform 1.12.2 has its new context menu off on iOS (`TextFieldSelectionState`'s
 * `observeTextToolbarVisibility`, Compose Foundation 1.12.2).
 */
internal class HoldableTextToolbar(private val platform: TextToolbar) : TextToolbar {
    private var holds = 0

    /**
     * Hides the menu and keeps it hidden until the returned release is called, and every other
     * hold's too. Calling the release again does nothing. Once released, the menu shows at the
     * field's next call, such as when the keyboard comes back and moves the field.
     */
    fun hold(): () -> Unit {
        holds++
        platform.hide()
        var released = false
        return {
            if (!released) {
                released = true
                holds--
            }
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?
    ) {
        if (holds == 0) {
            platform.showMenu(
                rect,
                onCopyRequested,
                onPasteRequested,
                onCutRequested,
                onSelectAllRequested,
                onAutofillRequested
            )
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        if (holds == 0) {
            platform.showMenu(
                rect,
                onCopyRequested,
                onPasteRequested,
                onCutRequested,
                onSelectAllRequested
            )
        }
    }

    override fun hide() = platform.hide()

    override val status: TextToolbarStatus get() = platform.status
}
