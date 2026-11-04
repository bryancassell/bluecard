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
     *
     * It's presented from the window's root view controller, not [over]: inside a Compose
     * `Dialog`, such as the merge's, [over] is the dialog's own view controller, which isn't in
     * the window's view controllers. Presenting from it worked, but UIKit logged that it "is not
     * supported, and may result in incorrect safe area insets and a corrupt root presentation…
     * Will become a hard exception in a future release" (iOS 27.0).
     */
    fun show(over: UIViewController, onRefused: () -> Unit) {
        val root = over.view.window?.rootViewController ?: over
        root.presentOnceSettled(controller, isClosed = { closed }, onRefused = onRefused)
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
 * button's tap calls the latest [buttons]' `onTap`. If UIKit won't show it, calls [onRefused].
 *
 * The keyboard and a text field's edit menu go down first, as the keyboard does for the date
 * picker (`CompletionDate.ios.kt`): the edit menu ("Select / Select All") otherwise stayed on top
 * of the alert, over its message. If the scout cancels, the keyboard comes back.
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
    val textToolbar = LocalTextToolbar.current
    val currentButtons by rememberUpdatedState(buttons)
    val currentOnRefused by rememberUpdatedState(onRefused)
    val alert = remember(viewController) {
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
    }
    DisposableEffect(alert) {
        keyboard?.hide()
        textToolbar.hide()
        alert.show(over = viewController) { currentOnRefused() }
        onDispose(alert::close)
    }
}
