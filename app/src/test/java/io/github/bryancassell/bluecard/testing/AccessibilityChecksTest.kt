package io.github.bryancassell.bluecard.testing

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.GraphicsMode

/**
 * Checks that [AccessibilityChecks] still finds problems. ATF and Compose's test code skip the
 * checks under Robolectric, so if an update changes how, every screen's checks could pass without
 * checking anything. Each control here has one problem, so each check is shown to work on its own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityChecksTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** Runs [test] under [AccessibilityChecks], as a screen's test is. */
    private fun checked(test: () -> Unit) = AccessibilityChecks(composeTestRule).apply(
        object : Statement() {
            override fun evaluate() = test()
        },
        Description.EMPTY
    ).evaluate()

    @Test
    fun controlWithNoLabel_fails() {
        assertThrows(AccessibilityViewCheckException::class.java) {
            checked { composeTestRule.setContent { Control(label = null) } }
        }
    }

    @Test
    fun smallControl_fails() {
        assertThrows(AccessibilityViewCheckException::class.java) {
            checked { composeTestRule.setContent { Control(size = 10.dp) } }
        }
    }

    @Test
    fun problemInADialog_fails() {
        assertThrows(AccessibilityViewCheckException::class.java) {
            checked {
                composeTestRule.setContent { Dialog(onDismissRequest = {}) { Control(10.dp) } }
            }
        }
    }

    @Test
    fun problemGoneByTheEnd_failsTheActionBeforeIt() {
        var shown by mutableStateOf(true)
        checked {
            composeTestRule.setContent {
                Column {
                    Button(onClick = {}) { Text("Save") }
                    if (shown) Control(size = 10.dp)
                }
            }
            assertThrows(AccessibilityViewCheckException::class.java) {
                composeTestRule.onNodeWithText("Save").performClick()
            }
            shown = false
        }
    }

    @Test
    fun labeledButton_passes_andLeavesTheFingerprintAsItWas() {
        checked {
            composeTestRule.setContent { Button(onClick = {}) { Text("Save") } }
            composeTestRule.onNodeWithText("Save").performClick()
        }

        assertEquals("robolectric", Build.FINGERPRINT)
    }

    @Composable
    private fun Control(size: Dp = 48.dp, label: String? = "Remove") {
        val labeled = if (label == null) {
            Modifier
        } else {
            Modifier.semantics {
                contentDescription = label
            }
        }
        Box(Modifier.size(size).then(labeled).clickable {})
    }
}
