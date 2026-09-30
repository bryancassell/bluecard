package io.github.bryancassell.bluecard.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.platform.LocalViewConfiguration
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
import org.robolectric.shadows.ShadowToast

/**
 * [rememberStartOtherApp], with Robolectric, which records the activities started and the
 * toasts shown. The test clock is stopped, so each test moves it past the double-tap timeout
 * itself.
 */
@RunWith(AndroidJUnit4::class)
class StartOtherAppTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val link = Intent(Intent.ACTION_VIEW, "https://www.scouting.org/".toUri())
    private val noApp = "No app on this phone can open the link."

    private lateinit var startOtherApp: (Intent, String) -> Unit
    private var doubleTapTimeoutMillis = 0L

    private fun show() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            doubleTapTimeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
            startOtherApp = rememberStartOtherApp()
        }
    }

    private fun start() = composeTestRule.runOnIdle { startOtherApp(link, noApp) }

    private fun assertStartedOnce() {
        assertEquals(link.dataString, shadowOf(application).nextStartedActivity?.dataString)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun start_startsTheApp() {
        show()

        start()

        assertStartedOnce()
    }

    @Test
    fun secondStart_withinTheDoubleTapTimeout_isIgnored() {
        show()

        start()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeoutMillis / 2)
        start()

        assertStartedOnce()
    }

    @Test
    fun start_afterTheDoubleTapTimeout_startsTheAppAgain() {
        show()
        start()
        assertStartedOnce()

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeoutMillis)
        start()

        assertStartedOnce()
    }

    @Test
    fun doubleStart_withNoApp_showsMessageOnce_andTheNextStartTriesAgain() {
        show()
        // Starting an activity nothing can handle now fails, as when parental controls block
        // the browser.
        shadowOf(application).checkActivities(true)

        start()
        start()

        assertEquals(noApp, ShadowToast.getTextOfLatestToast())
        assertEquals(1, ShadowToast.shownToastCount())
        assertNull(shadowOf(application).nextStartedActivity)

        // An app that can open it, such as one the scout has just installed.
        shadowOf(application).checkActivities(false)
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeoutMillis)
        start()

        assertStartedOnce()
    }
}
