package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.data.FileUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController

class IosOtherAppsTest {
    private val report = FileUrl(NSURL.fileURLWithPath("/tmp/Camping merit badge report.pdf"))

    /** A page or picker that keeps what it's asked to show, and shows [showing] already. */
    private class RecordingViewController(private val showing: UIViewController? = null) :
        UIViewController(nibName = null, bundle = null) {
        var shown: UIViewController? = null

        override fun presentedViewController(): UIViewController? = showing

        override fun presentViewController(
            viewControllerToPresent: UIViewController,
            animated: Boolean,
            completion: (() -> Unit)?
        ) {
            shown = viewControllerToPresent
        }
    }

    @Test
    fun share_showsTheShareSheetOverThePage() {
        val page = RecordingViewController()

        IosOtherApps(page).share(report, "application/pdf")

        assertIs<UIActivityViewController>(page.shown)
    }

    @Test
    fun share_whileAFilePickerShows_showsTheShareSheetOverIt() {
        val picker = RecordingViewController()
        val page = RecordingViewController(showing = picker)

        IosOtherApps(page).share(report, "application/pdf")

        assertNull(page.shown)
        assertIs<UIActivityViewController>(picker.shown)
    }

    @Test
    fun links_openNoApp_until349() {
        val otherApps = IosOtherApps(RecordingViewController())

        assertFalse(otherApps.openWebPage("https://www.scouting.org/merit-badges/camping/"))
        assertFalse(otherApps.dial("5551234567"))
        assertFalse(otherApps.email("counselor@example.com"))
    }
}
