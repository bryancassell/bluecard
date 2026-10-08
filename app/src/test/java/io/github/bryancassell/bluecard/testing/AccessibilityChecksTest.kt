package io.github.bryancassell.bluecard.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Checks that [enableAccessibilityChecksUnderRobolectric] still finds problems. ATF and Compose's
 * test code skip the checks under Robolectric, so if an update changes how, every screen's checks
 * could pass without checking anything.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityChecksTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun enableChecks() = composeTestRule.enableAccessibilityChecksUnderRobolectric()

    @Test
    fun smallControlWithNoLabel_fails() {
        composeTestRule.setContent { UnlabeledSmallControl() }

        assertThrows(AccessibilityViewCheckException::class.java) {
            composeTestRule.onNodeWithTag(UNLABELED).performClick()
        }
    }

    @Test
    fun smallControlWithNoLabelInADialog_fails() {
        composeTestRule.setContent { Dialog(onDismissRequest = {}) { UnlabeledSmallControl() } }

        assertThrows(AccessibilityViewCheckException::class.java) {
            composeTestRule.onNodeWithTag(UNLABELED).performClick()
        }
    }

    @Test
    fun labeledButton_passes() {
        composeTestRule.setContent { Button(onClick = {}) { Text("Save") } }

        composeTestRule.onNodeWithText("Save").performClick()
    }

    @Composable
    private fun UnlabeledSmallControl() {
        Box(Modifier.size(10.dp).testTag(UNLABELED).clickable {})
    }

    private companion object {
        const val UNLABELED = "unlabeled"
    }
}
