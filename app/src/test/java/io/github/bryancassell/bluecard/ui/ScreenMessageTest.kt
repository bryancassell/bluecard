package io.github.bryancassell.bluecard.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.LiveRegionReadouts
import io.github.bryancassell.bluecard.testing.waitRunningPostedWork
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What TalkBack reads out from a message that takes a screen's place. It reads a live region
 * whenever Compose reports a change to it, a node's first layout included, so a message composed
 * already showing, as after the phone rotates, was read out again (#281).
 */
@RunWith(AndroidJUnit4::class)
class ScreenMessageTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val restorationTester = StateRestorationTester(composeTestRule)

    private val loadFailed = "Couldn't load your data. Try closing and reopening BlueCard."

    // Tests change it after show(), as a screen's state would.
    private var message by mutableStateOf<String?>(null)

    private val readouts = LiveRegionReadouts()

    // Set again after the activity is recreated, as MainActivity sets its content each time.
    private lateinit var content: @Composable () -> Unit

    private fun setContent(content: @Composable () -> Unit) {
        this.content = content
        composeTestRule.activityRule.scenario.onActivity { it.setContent(content = content) }
    }

    /** Recreates the activity, as the phone does when it rotates, and shows the content again. */
    private fun rotate() {
        composeTestRule.activityRule.scenario.recreate()
        setContent(content)
    }

    /**
     * Shows [content] so that [restart] can follow, in place of [setContent], which [rotate]
     * needs.
     */
    private fun setRestartableContent(content: @Composable () -> Unit) =
        restorationTester.setContent(content)

    /**
     * Shows the content again as after the system stops BlueCard and the scout comes back: its
     * saved state is restored, but nothing it retained.
     */
    private fun restart() = restorationTester.emulateSavedInstanceStateRestore()

    /**
     * [message] in place of a screen's content, or the loading indicator while it's null. Each
     * message has its own place, as each has its own `when` branch on a screen.
     */
    @Composable
    private fun Screen() {
        val view = LocalView.current
        // Before the first layout, whose events Compose sends after the frame.
        SideEffect { readouts.listenTo(view) }
        when (val shown = message) {
            null -> ScreenLoadingIndicator()
            else -> key(shown) { ScreenMessage(shown) }
        }
    }

    private fun show(message: String?) {
        this.message = message
        setContent { Screen() }
    }

    @Test
    fun message_isReadOut_asItReplacesTheLoadingIndicator() {
        show(null)
        composeTestRule.waitRunningPostedWork()
        assertEquals(emptyList<String>(), readouts.sinceLastCall())

        message = loadFailed
        composeTestRule.waitRunningPostedWork()

        assertEquals(listOf(loadFailed), readouts.sinceLastCall())
    }

    // As when the scout comes back to a page whose data failed to load while they were on a later
    // page, or a screen that had loaded can't load what changed.
    @Test
    fun message_isReadOut_whenTheScreenIsComposedShowingIt() {
        show(loadFailed)
        composeTestRule.waitRunningPostedWork()

        assertEquals(listOf(loadFailed), readouts.sinceLastCall())
    }

    // Read out again, it held back the screen's heading by about 3 seconds.
    @Test
    fun message_isNotReadOutAgain_afterThePhoneRotates() {
        show(null)
        message = loadFailed
        composeTestRule.waitRunningPostedWork()
        readouts.sinceLastCall()

        rotate()
        composeTestRule.waitRunningPostedWork()

        composeTestRule.onNodeWithText(loadFailed).assertIsDisplayed()
        assertEquals(emptyList<String>(), readouts.sinceLastCall())
    }

    // Its place forgets the message once the loading indicator takes it.
    @Test
    fun sameMessage_afterTheLoadingIndicator_isReadOutAgain() {
        show(loadFailed)
        composeTestRule.waitRunningPostedWork()
        message = null
        composeTestRule.waitRunningPostedWork()
        readouts.sinceLastCall()

        message = loadFailed
        composeTestRule.waitRunningPostedWork()

        assertEquals(listOf(loadFailed), readouts.sinceLastCall())
    }

    @Test
    fun aDifferentMessage_afterThePhoneRotates_isReadOut() {
        show(loadFailed)
        composeTestRule.waitRunningPostedWork()
        rotate()
        composeTestRule.waitRunningPostedWork()
        readouts.sinceLastCall()

        message = "This badge's requirements aren't available."
        composeTestRule.waitRunningPostedWork()

        assertEquals(
            listOf("This badge's requirements aren't available."),
            readouts.sinceLastCall()
        )
    }

    // Kept with the screen's saved state, it waited until its place was next composed, which could
    // be after the screen had loaded and failed again (#329).
    @Test
    fun message_isReadOutAgain_afterTheSystemStopsBlueCard() {
        message = loadFailed
        setRestartableContent { Screen() }
        composeTestRule.waitRunningPostedWork()
        readouts.sinceLastCall()

        restart()
        composeTestRule.waitRunningPostedWork()

        assertEquals(listOf(loadFailed), readouts.sinceLastCall())
    }

    // The text rememberIsNewText is given, and what it gave back last.
    private var text by mutableStateOf("")
    private var isNew: Boolean? = null

    // Read by the content, so changing it recomposes it with the same text.
    private var recompositions by mutableStateOf(0)

    @Composable
    private fun IsNewText() {
        recompositions
        isNew = rememberIsNewText(text)
    }

    private fun showIsNewText(text: String) {
        this.text = text
        setContent { IsNewText() }
        composeTestRule.waitForIdle()
    }

    private fun isNewText(text: String): Boolean? {
        this.text = text
        composeTestRule.waitForIdle()
        return isNew
    }

    // TalkBack looks at a live region a little after the change, so it must still be one then.
    @Test
    fun isNewText_staysNew_whenRecomposedWithTheSameText() {
        showIsNewText("Star will no longer count as earned.")

        recompositions++
        composeTestRule.waitForIdle()

        assertEquals(true, isNew)
    }

    @Test
    fun isNewText_eachChange_isNew() {
        showIsNewText("Star will no longer count as earned.")

        assertEquals(true, isNewText("Star and Life will no longer count as earned."))
        assertEquals(true, isNewText("Star will no longer count as earned."))
    }

    @Test
    fun isNewText_afterThePhoneRotates_isntNew_untilItChanges() {
        showIsNewText("Star will no longer count as earned.")

        rotate()
        composeTestRule.waitForIdle()

        assertEquals(false, isNew)
        assertEquals(true, isNewText("Star and Life will no longer count as earned."))
    }

    @Test
    fun isNewText_afterTheSystemStopsBlueCard_isNew() {
        text = "Star will no longer count as earned."
        setRestartableContent { IsNewText() }
        composeTestRule.waitForIdle()

        restart()
        composeTestRule.waitForIdle()

        assertEquals(true, isNew)
    }
}
