package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
    val composeTestRule = createComposeRule()

    private val restorationTester = StateRestorationTester(composeTestRule)

    private val loadFailed = "Couldn't load your data. Try closing and reopening BlueCard."

    // Tests change it after show(), as a screen's state would.
    private var message by mutableStateOf<String?>(null)

    private lateinit var readouts: LiveRegionReadouts

    /** Shows [message] in place of a screen's content, or the loading indicator while it's null. */
    private fun show(message: String?) {
        this.message = message
        readouts = LiveRegionReadouts()
        restorationTester.setContent {
            val view = LocalView.current
            // Before the first layout, whose events Compose sends after the frame.
            SideEffect { readouts.listenTo(view) }
            LoadingOrMessage(this.message)
        }
    }

    @Test
    fun message_isReadOut_asItReplacesTheLoadingIndicator() {
        show(null)
        composeTestRule.waitRunningPostedWork()
        assertEquals(emptyList<String>(), readouts.sinceLastCall())

        message = loadFailed
        composeTestRule.waitRunningPostedWork()

        assertEquals(listOf(loadFailed), readouts.sinceLastCall().distinct())
    }

    // As when the scout comes back to a page whose data failed to load while they were on a later
    // page, or a screen that had loaded can't load what changed.
    @Test
    fun message_isReadOut_whenTheScreenIsComposedShowingIt() {
        show(loadFailed)
        composeTestRule.waitRunningPostedWork()

        assertEquals(listOf(loadFailed), readouts.sinceLastCall().distinct())
    }

    // Read out again, it held back the screen's heading by about 3 seconds.
    @Test
    fun message_isNotReadOutAgain_afterThePhoneRotates() {
        show(null)
        message = loadFailed
        composeTestRule.waitRunningPostedWork()
        readouts.sinceLastCall()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitRunningPostedWork()

        composeTestRule.onNodeWithText(loadFailed).assertIsDisplayed()
        assertEquals(emptyList<String>(), readouts.sinceLastCall())
    }

    @Test
    fun aDifferentMessage_afterThePhoneRotates_isReadOut() {
        show(loadFailed)
        composeTestRule.waitRunningPostedWork()
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitRunningPostedWork()
        readouts.sinceLastCall()

        message = "This badge's requirements aren't available."
        composeTestRule.waitRunningPostedWork()

        assertEquals(
            listOf("This badge's requirements aren't available."),
            readouts.sinceLastCall().distinct()
        )
    }

    // The text rememberIsNewText is given, and what it gave back last.
    private var text by mutableStateOf<String?>(null)
    private var isNew: Boolean? = null

    // Read by the content, so changing it recomposes it with the same text.
    private var recompositions by mutableStateOf(0)

    private fun showIsNewText(text: String?) {
        this.text = text
        restorationTester.setContent {
            recompositions
            isNew = rememberIsNewText(this.text)
        }
        composeTestRule.waitForIdle()
    }

    private fun isNewText(text: String?): Boolean? {
        this.text = text
        composeTestRule.waitForIdle()
        return isNew
    }

    @Test
    fun isNewText_noText_isntNew() {
        showIsNewText(null)

        assertEquals(false, isNew)
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
    fun isNewText_eachChange_isNew_andNoTextIsnt() {
        showIsNewText("Star will no longer count as earned.")

        assertEquals(true, isNewText("Star and Life will no longer count as earned."))
        assertEquals(false, isNewText(null))
        assertEquals(true, isNewText("Star will no longer count as earned."))
    }

    @Test
    fun isNewText_afterThePhoneRotates_isntNew_untilItChanges() {
        showIsNewText("Star will no longer count as earned.")

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(false, isNew)
        assertEquals(true, isNewText("Star and Life will no longer count as earned."))
    }
}
