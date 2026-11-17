package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyle
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyle
import platform.UIKit.UIViewController

/**
 * A button of a [UIKitAlert]: its [title], UIKit's [style] for it (default, cancel, or destructive,
 * which UIKit draws red), and what tapping it does ([onTap]). Tapping any button closes the alert.
 */
internal class AlertButton(val title: String, val style: UIAlertActionStyle, val onTap: () -> Unit)

/**
 * iOS's own alert or action sheet ([style]), a [UIAlertController] with [title], [message] and
 * [buttons] in order. UIKit draws it in each iOS version's look, and places a cancel button where
 * that version puts it: on iOS 27, first in a row of two buttons, and last in a stack of three.
 * There, an action sheet with no view to point to, as these have, shows in the middle of the
 * window, as an alert does.
 */
// Nothing UIKit keeps refers back to this object, as in DatePickerSheet: each button's handler
// captures only its button. Kotlin/Native can't free a cycle that passes through an Objective-C
// object: https://kotlinlang.org/docs/native-arc-integration.html#retain-cycles
internal class UIKitAlert(
    title: String,
    message: String?,
    style: UIAlertControllerStyle,
    buttons: List<AlertButton>
) {
    val controller: UIAlertController =
        UIAlertController.alertControllerWithTitle(title, message, style).apply {
            for (button in buttons) {
                addAction(
                    UIAlertAction.actionWithTitle(button.title, button.style) {
                        button.onTap()
                    }
                )
            }
        }

    private var closed = false

    /**
     * Shows the alert over what's on top of [over]'s window, once a presentation or dismissal
     * there ends ([presentOnceSettled]). If UIKit won't show it, calls [onRefused].
     */
    fun show(over: UIViewController, onRefused: () -> Unit) {
        over.presentOnceSettled(controller, isClosed = { closed }, onRefused = onRefused)
    }

    /** Closes the alert, or keeps it from showing if it hasn't yet. */
    fun close() {
        closed = true
        controller.dismissOnceSettled()
    }
}

/**
 * Shows a [UIKitAlert] while this is composed, so it closes once the page stops asking, as a
 * Material dialog does. A new [title] or [message] shows in it, as in a Material dialog, and a
 * button's tap calls the latest [buttons]' `onTap`. A new button title or style shows a new alert
 * in its place. UIKit closes the alert as any button is tapped, so each `onTap` must stop
 * composing it, as every caller's does, or it stays composed with nothing shown. If UIKit won't
 * show it, calls [onRefused].
 *
 * Its buttons take no taps for the double-tap timeout as it opens ([isOpeningForDoubleTap]), as
 * Android's `ConfirmDialog` ignores them. On iOS 17 and 18 an action sheet rises from the bottom,
 * where the button that opened it, such as Clear progress, may be, so the second tap of a double
 * tap could otherwise land on its red button. On the iOS 27 simulator, a tap on the alert or the
 * dimmed area meanwhile did nothing, and the alert stayed open.
 *
 * The keyboard goes down first, as for the date picker (`CompletionDate.ios.kt`), and a text
 * field's edit menu ("Select / Select All") stays hidden while the alert shows
 * ([HoldableTextToolbar]), since it otherwise stayed on top of the alert, over its message. If
 * the scout cancels, the keyboard comes back, as UIKit gives the field back its keyboard. If UIKit
 * won't show the alert, the keyboard comes back then too, and the menu is let go, as a page may
 * keep asking, as `DamagedProgressNotice` does until its next launch.
 */
@Composable
internal fun SystemAlert(
    title: String,
    message: String?,
    style: UIAlertControllerStyle,
    buttons: List<AlertButton>,
    onRefused: () -> Unit
) {
    val viewController = LocalUIViewController.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Null in a Compose Dialog, which provides its own window's toolbar, as the Merge dialog does.
    // Neither it nor Data management under it has a text field.
    val textToolbar = LocalTextToolbar.current as? HoldableTextToolbar
    val currentButtons by rememberUpdatedState(buttons)
    val currentOnRefused by rememberUpdatedState(onRefused)
    val opening = isOpeningForDoubleTap()
    val alert = remember(viewController, buttons.map { it.title to it.style }) {
        UIKitAlert(
            title,
            message,
            style,
            buttons.mapIndexed { index, button ->
                AlertButton(button.title, button.style) { currentButtons[index].onTap() }
            }
        )
    }
    SideEffect {
        alert.controller.title = title
        alert.controller.message = message
        alert.controller.view.userInteractionEnabled = !opening
    }
    DisposableEffect(alert) {
        keyboard?.hide()
        val releaseTextToolbar = textToolbar?.hold() ?: {}
        alert.show(over = viewController) {
            releaseTextToolbar()
            keyboard?.show()
            currentOnRefused()
        }
        onDispose {
            releaseTextToolbar()
            alert.close()
        }
    }
}
