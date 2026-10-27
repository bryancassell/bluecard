package io.github.bryancassell.bluecard.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/**
 * [AndroidOtherApps]' intents, with Robolectric, which records the activities started and the
 * toasts shown. They're started from an activity, as the screens' are: from the application's
 * context, Android would need a new task for each.
 */
@RunWith(AndroidJUnit4::class)
class AndroidOtherAppsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val otherApps = AndroidOtherApps(activity.get())

    @After
    fun closeActivity() {
        activity.close()
    }

    private fun started(): Intent? = shadowOf(application).nextStartedActivity

    @Test
    fun openWebPage_viewsTheUrl() {
        assertTrue(otherApps.openWebPage("https://www.scouting.org/"))

        val started = started()
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.scouting.org/", started?.dataString)
    }

    @Test
    fun dial_fillsInTheNumberAsTyped() {
        assertTrue(otherApps.dial("555 123-4567"))

        val started = started()
        assertEquals(Intent.ACTION_DIAL, started?.action)
        assertEquals("555 123-4567", started?.data?.schemeSpecificPart)
        assertEquals("tel", started?.data?.scheme)
    }

    // Some email apps show the address as the link spells it, so @ and + stay as they are, while
    // anything that would change the link is encoded.
    @Test
    fun email_keepsAtAndPlus_andEncodesWhatWouldChangeTheLink() {
        assertTrue(otherApps.email("pat+scouts@example.com?cc=x#y"))

        val started = started()
        assertEquals(Intent.ACTION_SENDTO, started?.action)
        assertEquals("mailto:pat+scouts@example.com%3Fcc%3Dx%23y", started?.dataString)
    }

    @Test
    fun start_withNoApp_returnsFalse_andStartsNothing() {
        // Starting an activity nothing can handle now fails, as when parental controls block
        // the browser.
        shadowOf(application).checkActivities(true)

        assertFalse(otherApps.openWebPage("https://www.scouting.org/"))
        assertFalse(otherApps.dial("5551234567"))
        assertFalse(otherApps.email("pat@example.com"))
        assertNull(started())
    }

    @Test
    fun share_opensTheShareSheet_withTheFile_andLetsTheAppPickedReadIt() {
        val report = Uri.parse("content://reports/first-aid.pdf")

        otherApps.share(report, "application/pdf")

        val chooser = started()
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val send =
            IntentCompat.getParcelableExtra(chooser!!, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertEquals(
            report,
            IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        )
        assertEquals(report, send.clipData?.getItemAt(0)?.uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun showMessage_showsAToast() {
        otherApps.showMessage("No app on this phone can open the link.")

        assertEquals("No app on this phone can open the link.", ShadowToast.getTextOfLatestToast())
    }
}
