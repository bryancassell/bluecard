package io.github.bryancassell.bluecard.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import platform.UIKit.UIViewController

// Nothing can be presented here, with no app window, so UIKit refuses every presentation.
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
    fun presentOnceSettled_onceClosed_presentsNothing() {
        val over = UIViewController()

        over.presentOnceSettled(
            UIViewController(),
            isClosed = { true },
            onRefused = { events += "refused" }
        )

        assertNull(over.presentedViewController)
        assertEquals(emptyList(), events)
    }

    @Test
    fun dismissOnceSettled_whenNotPresented_doesNothing() {
        val controller = UIViewController()

        controller.dismissOnceSettled()

        assertNull(controller.presentingViewController)
    }
}
