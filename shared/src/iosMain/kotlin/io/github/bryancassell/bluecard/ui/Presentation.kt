package io.github.bryancassell.bluecard.ui

import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator

/**
 * Presents [controller] over what's on top of this view controller's window: its root view
 * controller, or the last one presented over it that isn't going ([topPresented]). It waits until
 * a presentation or dismissal under way there ends, since UIKit refuses to present during one, and
 * does nothing if [isClosed] by then, as when the page stopped asking meanwhile. If UIKit still
 * won't present it, calls [onRefused].
 *
 * It's presented from the window's root, not this one: inside a Compose `Dialog`, such as the
 * merge's, `LocalUIViewController` is the dialog's own view controller, which isn't in the
 * window's view controllers. Presenting from it worked, but UIKit logged that it "is not
 * supported, and may result in incorrect safe area insets and a corrupt root presentation… Will
 * become a hard exception in a future release" (iOS 27.0).
 *
 * The wait asks the top controller for the transition, which also covers one between two
 * controllers above the root. While the top one is being dismissed, as the file picker is when
 * the import asks what to do with its file, [topPresented] gives the one under it, which is in
 * that dismissal too. If UIKit won't queue the wait, as when the transition is already ending, it
 * presents at once, and so calls [onRefused] if UIKit refuses.
 */
internal fun UIViewController.presentOnceSettled(
    controller: UIViewController,
    isClosed: () -> Boolean,
    onRefused: () -> Unit
) {
    val root = view.window?.rootViewController ?: this
    val present = {
        if (!isClosed()) {
            root.topPresented().presentViewController(
                controller,
                animated = true,
                completion = null
            )
            if (controller.presentingViewController == null) onRefused()
        }
    }
    val transition = root.topPresented().transitionCoordinator
    if (transition == null || !transition.animateAlongsideTransition(null, { present() })) present()
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
 * ends, unless it ended dismissed. If UIKit won't queue that, it's dismissed at once.
 */
internal fun UIViewController.dismissOnceSettled() {
    val transition = transitionCoordinator
    if (transition == null ||
        !transition.animateAlongsideTransition(null, { dismissIfPresented() })
    ) {
        dismissIfPresented()
    }
}

private fun UIViewController.dismissIfPresented() {
    if (presentingViewController != null) dismissViewControllerAnimated(true, completion = null)
}
