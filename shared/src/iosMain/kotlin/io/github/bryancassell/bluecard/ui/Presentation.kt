package io.github.bryancassell.bluecard.ui

import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator

/**
 * Presents [controller] over the view controller on top: this one, or the last one presented over
 * it that isn't going ([topPresented]). It waits until a presentation or dismissal under way here
 * ends, since UIKit refuses to present during one, and does nothing if [isClosed] by then, as when
 * the page stopped asking meanwhile. If UIKit still won't present it, calls [onRefused].
 */
internal fun UIViewController.presentOnceSettled(
    controller: UIViewController,
    isClosed: () -> Boolean,
    onRefused: () -> Unit
) {
    val present = {
        if (!isClosed()) {
            topPresented().presentViewController(controller, animated = true, completion = null)
            if (controller.presentingViewController == null) onRefused()
        }
    }
    val transition = transitionCoordinator
    if (transition == null) present() else transition.animateAlongsideTransition(null) { present() }
}

/**
 * The view controller on top of this one: the last one presented over it, one over another, that
 * isn't being dismissed, or this one if none is.
 */
internal fun UIViewController.topPresented(): UIViewController {
    var top = this
    while (true) {
        val next = top.presentedViewController
        if (next == null || next.isBeingDismissed()) return top
        top = next
    }
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
