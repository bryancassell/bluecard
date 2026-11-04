package io.github.bryancassell.bluecard.ui

import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator

/**
 * Presents [controller] over this view controller once a presentation or dismissal under way here
 * ends, since UIKit refuses to present during one. Unless [isClosed] by then, as when the page
 * stopped asking meanwhile. If UIKit still won't, as while another sheet shows, calls [onRefused].
 */
internal fun UIViewController.presentOnceSettled(
    controller: UIViewController,
    isClosed: () -> Boolean,
    onRefused: () -> Unit
) {
    val present = {
        if (!isClosed()) {
            presentViewController(controller, animated = true, completion = null)
            if (controller.presentingViewController == null) onRefused()
        }
    }
    val transition = transitionCoordinator
    if (transition == null) present() else transition.animateAlongsideTransition(null) { present() }
}

/**
 * Dismisses this view controller if it's presented. UIKit ignores a dismissal while it appears, or
 * while the scout swipes a sheet down and may yet drag it back up, so then it's dismissed once that
 * ends, unless it ended dismissed.
 */
internal fun UIViewController.dismissOnceSettled() {
    val transition = transitionCoordinator
    if (transition == null) {
        dismissIfPresented()
    } else {
        transition.animateAlongsideTransition(null) { dismissIfPresented() }
    }
}

private fun UIViewController.dismissIfPresented() {
    if (presentingViewController != null) dismissViewControllerAnimated(true, completion = null)
}
