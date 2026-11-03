package io.github.bryancassell.bluecard.ui

import io.github.bryancassell.bluecard.data.FileUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertController
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

    /** Keeps the links it opens, which an app opens if [appCanOpen]. */
    private class RecordingUrlOpener(private val appCanOpen: Boolean = true) : UrlOpener {
        val opened = mutableListOf<String?>()

        override fun canOpen(url: NSURL) = appCanOpen

        override fun open(url: NSURL) {
            opened += url.absoluteString
        }
    }

    private val urls = RecordingUrlOpener()

    private fun otherApps(
        page: UIViewController = RecordingViewController(),
        urls: UrlOpener = this.urls
    ) = IosOtherApps(page, "OK", urls)

    @Test
    fun share_showsTheShareSheetOverThePage() {
        val page = RecordingViewController()

        otherApps(page).share(report, "application/pdf")

        assertIs<UIActivityViewController>(page.shown)
    }

    @Test
    fun share_whileAFilePickerShows_showsTheShareSheetOverIt() {
        val picker = RecordingViewController()
        val page = RecordingViewController(showing = picker)

        otherApps(page).share(report, "application/pdf")

        assertNull(page.shown)
        assertIs<UIActivityViewController>(picker.shown)
    }

    @Test
    fun openWebPage_opensTheLink() {
        val opened = otherApps().openWebPage("https://www.scouting.org/merit-badges/camping/")

        assertTrue(opened)
        assertEquals(listOf<String?>("https://www.scouting.org/merit-badges/camping/"), urls.opened)
    }

    @Test
    fun dial_opensTheNumberAsTyped_withoutSpaces() {
        val opened = otherApps().dial("+1 (555) 123-4567")

        assertTrue(opened)
        assertEquals(listOf<String?>("tel:+1(555)123-4567"), urls.opened)
    }

    // A # would end the number, and the rest would be the link's fragment.
    @Test
    fun dial_encodesWhatWouldChangeTheLink() {
        otherApps().dial("555-1234#2")

        assertEquals(listOf<String?>("tel:555-1234%232"), urls.opened)
    }

    @Test
    fun email_keepsAtAndPlus() {
        val opened = otherApps().email("alex+scouts@example.com")

        assertTrue(opened)
        assertEquals(listOf<String?>("mailto:alex+scouts@example.com"), urls.opened)
    }

    // A ? would start the link's query, such as a subject, and a space isn't allowed in it.
    @Test
    fun email_encodesWhatWouldChangeTheLink() {
        otherApps().email("alex scout?@example.com")

        assertEquals(listOf<String?>("mailto:alex%20scout%3F@example.com"), urls.opened)
    }

    @Test
    fun links_thatNoAppCanOpen_openNothing() {
        val urls = RecordingUrlOpener(appCanOpen = false)
        val otherApps = otherApps(urls = urls)

        assertFalse(otherApps.openWebPage("https://www.scouting.org/merit-badges/camping/"))
        assertFalse(otherApps.dial("5551234567"))
        assertFalse(otherApps.email("counselor@example.com"))
        assertEquals(emptyList(), urls.opened)
    }

    // Saving replaces half an emoji (#390), but if one got through, UTF-8 couldn't encode it.
    @Test
    fun link_thatCantBeEncoded_opensNothing() {
        assertFalse(otherApps().dial("555 1234 \uD83D"))
        assertEquals(emptyList(), urls.opened)
    }

    @Test
    fun showMessage_showsAnAlert_withTheTextAndOk() {
        val page = RecordingViewController()

        otherApps(page).showMessage("No app on this phone can call the number.")

        val alert = assertIs<UIAlertController>(page.shown)
        assertEquals("No app on this phone can call the number.", alert.title)
        assertNull(alert.message)
        val action = assertIs<UIAlertAction>(alert.actions.single())
        assertEquals("OK", action.title)
    }

    @Test
    fun showMessage_whileSomethingShows_showsTheAlertOverIt() {
        val sheet = RecordingViewController()
        val page = RecordingViewController(showing = sheet)

        otherApps(page).showMessage("No app on this phone can send email.")

        assertNull(page.shown)
        assertIs<UIAlertController>(sheet.shown)
    }
}
