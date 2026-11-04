package io.github.bryancassell.bluecard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import platform.UIKit.UIViewController

// Nothing can be presented here, with no app window, so UIKit refuses every presentation. The
// view controllers that say what they present, and record what's presented from them, stand in
// for UIKit's.
class PresentationTest {
    private val events = mutableListOf<String>()

    @Test
    fun presentOnceSettled_whenUIKitWont_callsOnRefused() {
        UIViewController().presentOnceSettled(
            UIViewController(),
            isClosed = { false },
            onRefused = { events += "refused" }
        )

        assertEquals(listOf("refused"), events)
    }

    @Test
    fun presentOnceSettled_presentsFromTheViewControllerOnTop() {
        val top = Presenting(name = "top")
        val root = Presenting(name = "root", presented = top)

        root.presentOnceSettled(UIViewController(), isClosed = { false }, onRefused = {})

        assertEquals(listOf("presented from top"), events)
    }

    @Test
    fun presentOnceSettled_onceClosed_presentsNothing() {
        val root = Presenting(name = "root")

        root.presentOnceSettled(
            UIViewController(),
            isClosed = { true },
            onRefused = { events += "refused" }
        )

        assertEquals(emptyList(), events)
    }

    @Test
    fun topPresented_withNothingPresented_isItself() {
        val root = Presenting(name = "root")

        assertSame(root, root.topPresented())
    }

    @Test
    fun topPresented_isTheLastPresented_oneOverAnother() {
        val top = Presenting(name = "top")
        val middle = Presenting(name = "middle", presented = top)
        val root = Presenting(name = "root", presented = middle)

        assertSame(top, root.topPresented())
    }

    // As the file picker is while it closes, before the import asks what to do with the file.
    @Test
    fun topPresented_leavesOutOneBeingDismissed() {
        val middle = Presenting(name = "middle", presented = Presenting(name = "top", going = true))
        val root = Presenting(name = "root", presented = middle)

        assertSame(middle, root.topPresented())
    }

    @Test
    fun dismissOnceSettled_whenNotPresented_doesNothing() {
        val controller = UIViewController()

        controller.dismissOnceSettled()

        assertNull(controller.presentingViewController)
    }

    private inner class Presenting(
        private val name: String,
        private val presented: UIViewController? = null,
        private val going: Boolean = false
    ) : UIViewController(nibName = null, bundle = null) {
        override fun presentedViewController(): UIViewController? = presented

        override fun isBeingDismissed(): Boolean = going

        override fun presentViewController(
            viewControllerToPresent: UIViewController,
            animated: Boolean,
            completion: (() -> Unit)?
        ) {
            events += "presented from $name"
        }
    }
}
