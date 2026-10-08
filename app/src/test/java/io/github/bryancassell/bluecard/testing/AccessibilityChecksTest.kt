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
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.rules.RuleChain
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
    private val composeTestRule = createComposeRule()

    /**
     * Runs [test] with [AccessibilityChecks] inside [composeTestRule], as a screen's test class
     * does, or outside it if [checksOutside].
     */
    private fun checked(checksOutside: Boolean = false, test: () -> Unit) {
        val checks = AccessibilityChecks(composeTestRule)
        val rules = if (checksOutside) {
            RuleChain.outerRule(checks).around(composeTestRule)
        } else {
            RuleChain.outerRule(composeTestRule).around(checks)
        }
        rules.apply(
            object : Statement() {
                override fun evaluate() = test()
            },
            Description.EMPTY
        ).evaluate()
    }

    /** Runs [test] and checks that it fails on [check]'s result, and only on that. */
    private fun assertFailsOn(check: Class<out AccessibilityCheck>, test: () -> Unit) {
        val failure = assertThrows(AccessibilityViewCheckException::class.java, test)
        assertEquals(listOf(check), failure.results.map { it.sourceCheckClass })
    }

    @Test
    fun controlWithNoLabel_fails() {
        assertFailsOn(SpeakableTextPresentCheck::class.java) {
            checked { composeTestRule.setContent { Control(label = null) } }
        }
    }

    @Test
    fun smallControl_fails() {
        assertFailsOn(TouchTargetSizeCheck::class.java) {
            checked { composeTestRule.setContent { Control(size = 10.dp) } }
        }
    }

    @Test
    fun problemInADialog_fails() {
        assertFailsOn(TouchTargetSizeCheck::class.java) {
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
            assertFailsOn(TouchTargetSizeCheck::class.java) {
                composeTestRule.onNodeWithText("Save").performClick()
            }
            assertEquals("robolectric", Build.FINGERPRINT)
            shown = false
        }
    }

    // Outside the compose rule, the page is gone when the test ends, so nothing would be checked.
    @Test
    fun checksOutsideTheComposeRule_fail() {
        assertThrows(IllegalStateException::class.java) {
            checked(checksOutside = true) {
                composeTestRule.setContent { Button(onClick = {}) { Text("Save") } }
            }
        }
    }

    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    @Test
    fun defaultGraphics_fail() {
        assertThrows(IllegalStateException::class.java) {
            checked { composeTestRule.setContent { Button(onClick = {}) { Text("Save") } } }
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
