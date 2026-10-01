package io.github.bryancassell.bluecard.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
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
class OtherAppStarterTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val link = Intent(Intent.ACTION_VIEW, "https://www.scouting.org/".toUri())
    private val noApp = "No app on this phone can open the link."

    private lateinit var startOtherApp: OtherAppStarter
    private lateinit var picker: ActivityResultLauncher<String>
    private var doubleTapTimeoutMillis = 0L

    /** The inputs the file picker was launched with. */
    private val launched = mutableListOf<String>()

    /** Whether no app can handle the file picker's intent. */
    private var noPicker = false

    // Launches nothing, so the launches can be counted.
    private val resultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                if (noPicker) throw ActivityNotFoundException()
                launched += input as String
            }
        }
    }

    private fun show() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            doubleTapTimeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
            startOtherApp = rememberStartOtherApp()
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides resultRegistryOwner
            ) {
                picker = rememberLauncherForActivityResult(CreateDocument("application/pdf")) {}
            }
        }
    }

    private fun start() = composeTestRule.runOnIdle { startOtherApp(link, noApp) }

    private fun launchPicker() = composeTestRule.runOnIdle {
        startOtherApp.launch(picker, "report.pdf", noApp)
    }

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

    @Test
    fun launch_launchesIt() {
        show()

        launchPicker()

        assertEquals(listOf("report.pdf"), launched)
    }

    // A screen's controls share one starter, so a quick tap on a second control is ignored.
    @Test
    fun launch_withinTheDoubleTapTimeoutOfAStart_isIgnored() {
        show()

        start()
        launchPicker()

        assertStartedOnce()
        assertEquals(emptyList<String>(), launched)

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeoutMillis)
        launchPicker()
        assertEquals(listOf("report.pdf"), launched)
    }

    @Test
    fun launch_withNoApp_showsMessage() {
        show()
        noPicker = true

        launchPicker()

        assertEquals(noApp, ShadowToast.getTextOfLatestToast())
    }

    private var taps = 0

    private fun tap() = composeTestRule.runOnIdle { startOtherApp.tap { taps++ } }

    @Test
    fun tap_runsItsAction_onceInTheDoubleTapTimeout() {
        show()

        tap()
        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeoutMillis / 2)
        tap()
        assertEquals(1, taps)

        composeTestRule.mainClock.advanceTimeBy(doubleTapTimeoutMillis)
        tap()
        assertEquals(2, taps)
    }

    // Share report then the official link: the share sheet opens, not the browser too.
    @Test
    fun start_withinTheDoubleTapTimeoutOfATap_isIgnored() {
        show()

        tap()
        start()

        assertEquals(1, taps)
        assertNull(shadowOf(application).nextStartedActivity)
    }
}
