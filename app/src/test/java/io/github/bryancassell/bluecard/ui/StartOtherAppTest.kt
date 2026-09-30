package io.github.bryancassell.bluecard.ui

import android.app.Application
import android.content.Intent
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowViewRootImpl

/**
 * [rememberStartOtherApp], with Robolectric, which records the activities started and the
 * toasts shown. The other app never really opens, so the tests change the window's focus as
 * the platform does when it covers BlueCard and when the scout comes back.
 */
@RunWith(AndroidJUnit4::class)
class StartOtherAppTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val link = Intent(Intent.ACTION_VIEW, "https://www.scouting.org/".toUri())
    private val noApp = "No app on this phone can open the link."

    private lateinit var startOtherApp: (Intent, String) -> Unit
    private lateinit var view: View

    private fun show() {
        composeTestRule.setContent {
            view = LocalView.current
            startOtherApp = rememberStartOtherApp()
        }
    }

    private fun start() = composeTestRule.runOnIdle { startOtherApp(link, noApp) }

    private fun startedActivity(): Intent? = shadowOf(application).nextStartedActivity

    private fun setWindowFocused(focused: Boolean) {
        composeTestRule.runOnIdle {
            Shadow.extract<ShadowViewRootImpl>(view.rootView.parent)
                .callWindowFocusChanged(focused)
        }
    }

    @Test
    fun start_startsTheApp() {
        show()

        start()

        assertEquals(link.dataString, startedActivity()?.dataString)
    }

    @Test
    fun secondStart_whileTheAppOpens_isIgnored() {
        show()

        start()
        start()
        // The other app has taken focus, but BlueCard can still be under the finger.
        setWindowFocused(false)
        start()

        assertEquals(link.dataString, startedActivity()?.dataString)
        assertNull(startedActivity())
    }

    @Test
    fun start_afterComingBackToBlueCard_startsTheAppAgain() {
        show()
        start()
        startedActivity()

        setWindowFocused(false)
        setWindowFocused(true)
        start()

        assertEquals(link.dataString, startedActivity()?.dataString)
    }

    @Test
    fun start_withNoApp_showsMessage_andTheNextStartTriesAgain() {
        show()
        // Starting an activity nothing can handle now fails, as when parental controls block
        // the browser.
        shadowOf(application).checkActivities(true)

        start()

        assertEquals(noApp, ShadowToast.getTextOfLatestToast())
        assertNull(startedActivity())

        // An app that can open it, such as one the scout has just installed.
        shadowOf(application).checkActivities(false)
        start()

        assertEquals(link.dataString, startedActivity()?.dataString)
    }
}
