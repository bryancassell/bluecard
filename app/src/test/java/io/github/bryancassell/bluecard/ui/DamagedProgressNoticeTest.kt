package io.github.bryancassell.bluecard.ui

import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.enableAccessibilityChecksUnderRobolectric
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DamagedProgressNoticeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun enableAccessibilityChecks() = composeTestRule.enableAccessibilityChecksUnderRobolectric()

    private var dismissals = 0

    private fun show() {
        composeTestRule.setContent { DamagedProgressNotice(onDismiss = { dismissals++ }) }
    }

    @Test
    fun saysProgressWasDamaged_andWhatIsKept() {
        show()

        composeTestRule.onNodeWithText("Your progress couldn't be read").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "The progress saved on this phone was damaged, so BlueCard started over without " +
                "it. Your name and unit number are kept. If you exported your progress, you " +
                "can import it from Data management."
        ).assertIsDisplayed()
    }

    @Test
    fun ok_dismissesIt() {
        show()

        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(1, dismissals)
    }

    @Test
    fun back_keepsIt() {
        show()

        // Espresso's pressBack doesn't reach the dialog's window under Robolectric, so Back is
        // sent to the dialog itself.
        composeTestRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
                .onBackPressed()
        }

        assertEquals(0, dismissals)
        composeTestRule.onNode(isDialog()).assertIsDisplayed()
    }
}
