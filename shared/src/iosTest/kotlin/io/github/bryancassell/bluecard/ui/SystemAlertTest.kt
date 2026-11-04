package io.github.bryancassell.bluecard.ui

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.cinterop.autoreleasepool
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertControllerStyle
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIViewController

// The alert can't be shown here, with no app window, so UIKit refuses it. Nor can its buttons be
// tapped: UIKit has no public way to, and Kotlin/Native can't call the block UIKit keeps for each
// ("Converting Obj-C blocks with non-reference-typed arguments to kotlin.Any is not supported").
// They're checked on the simulator.
class SystemAlertTest {
    private val events = mutableListOf<String>()

    private fun alert(style: UIAlertControllerStyle = ActionSheet) = UIKitAlert(
        title = "Clear progress?",
        message = "What you recorded for it will be removed.",
        style = style,
        buttons = listOf(
            AlertButton("Clear", UIAlertActionStyleDestructive) {
                events += "cleared"
            },
            AlertButton("Cancel", UIAlertActionStyleCancel) {
                events += "cancelled"
            }
        )
    )

    @Test
    fun controller_hasTheTitleAndMessage_inTheStyleGiven() {
        for (style in listOf(ActionSheet, Alert)) {
            val controller = alert(style).controller

            assertEquals("Clear progress?", controller.title)
            assertEquals("What you recorded for it will be removed.", controller.message)
            assertEquals(style, controller.preferredStyle)
        }
    }

    @Test
    fun controller_hasTheButtons_inOrder_withTheirStyles() {
        val actions = alert().controller.actions.map { it as UIAlertAction }

        assertEquals(listOf("Clear", "Cancel"), actions.map { it.title })
        assertEquals(
            listOf(
                UIAlertActionStyleDestructive,
                UIAlertActionStyleCancel
            ),
            actions.map { it.style }
        )
    }

    @Test
    fun show_whenUIKitWont_callsOnRefused() {
        alert().show(over = UIViewController()) { events += "refused" }

        assertEquals(listOf("refused"), events)
    }

    @Test
    fun show_onceClosed_doesNothing() {
        val alert = alert()
        alert.close()

        alert.show(over = UIViewController()) { events += "refused" }

        assertEquals(emptyList(), events)
    }

    // Kotlin/Native can't free a cycle through Objective-C objects, so a button's handler that
    // referred back to the alert would keep the alert, and its page's callbacks with it.
    @OptIn(NativeRuntimeApi::class, ExperimentalNativeApi::class)
    @Test
    fun alert_onceNothingRefersToIt_isFreed() {
        val alert = weakAlert()

        GC.collect()

        assertNull(alert.value)
    }

    // Made in a function of its own, so no reference to the alert stays on the test's stack.
    @OptIn(ExperimentalNativeApi::class)
    private fun weakAlert() = autoreleasepool { WeakReference(alert()) }

    private companion object {
        val ActionSheet = UIAlertControllerStyleActionSheet
        val Alert = UIAlertControllerStyleAlert
    }
}
