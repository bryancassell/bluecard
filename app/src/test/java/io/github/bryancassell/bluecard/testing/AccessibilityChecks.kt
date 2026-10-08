package io.github.bryancassell.bluecard.testing

import android.os.Build
import android.view.View
import androidx.compose.ui.test.ComposeAccessibilityValidator
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.annotation.GraphicsMode
import org.robolectric.config.ConfigurationRegistry
import org.robolectric.shadows.ShadowBuild

/**
 * A screen tall enough for Material's date picker to give each day a 48dp touch target, for every
 * test that opens it: a small phone's. On Robolectric's default screen (320x470dp), a month that
 * spans six weeks gives each day 47dp. On a short window, such as a phone's in landscape, the
 * app's picker has the same problem (#282).
 */
const val DATE_PICKER_SCREEN = "w360dp-h560dp"

/**
 * Runs Google's Accessibility Test Framework (ATF) checks on every window, dialogs included,
 * before each click, scroll, touch, key or text input the test performs, and again on the state
 * the test ends in. Compose runs them before those actions only: a semantics action, a focus
 * request, or replacing or clearing a field's text runs none. They catch problems such as a control with no label for screen readers
 * or a touch target smaller than 48dp, and fail the test with ATF's
 * `AccessibilityViewCheckException`. They take no screenshots, so ATF's contrast checks don't run.
 *
 * Compose's own `enableAccessibilityChecks()` checks nothing under Robolectric: ATF sees
 * `Build.FINGERPRINT` is "robolectric" and looks only at Views, never at composables
 * ([ATF #40](https://github.com/google/Accessibility-Test-Framework-for-Android/issues/40)). So
 * this gives ATF another fingerprint while it checks, and only then, because Compose's test code
 * reads it too. ATF also finds nothing under Robolectric's default graphics, so the test class
 * needs `@GraphicsMode(NATIVE)`. `AccessibilityChecksTest` fails if an update stops the checks
 * finding problems.
 *
 * The page must still be showing when the test ends, so this rule runs inside [composeTestRule]:
 * `@get:Rule(order = 0)` on that and `@get:Rule(order = 1)` on this.
 */
class AccessibilityChecks(private val composeTestRule: ComposeTestRule) : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            check(
                ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.NATIVE
            ) { "Accessibility checks find nothing without @GraphicsMode(NATIVE)" }
            enableChecks()
            base.evaluate()
            // A test that only asserts has no action to check before, and nor does the state a
            // test ends in. Outside the compose rule, the page is gone and Compose would check
            // nothing, so a missing page fails here.
            composeTestRule.onAllNodes(isRoot()).onFirst().assertExists()
                .tryPerformAccessibilityChecks()
        }
    }

    // setComposeAccessibilityValidator is the hook enableAccessibilityChecks() uses. It's public
    // but restricted to Compose's own libraries, so a Compose update could change it.
    @Suppress("RestrictedApi")
    private fun enableChecks() {
        val validator = AccessibilityValidator().setCaptureScreenshots(false)
        (composeTestRule as AndroidComposeTestRule<*, *>).setComposeAccessibilityValidator(
            object : ComposeAccessibilityValidator {
                override fun check(view: View) {
                    val fingerprint = Build.FINGERPRINT
                    ShadowBuild.setFingerprint("not-robolectric")
                    try {
                        validator.check(view)
                    } finally {
                        ShadowBuild.setFingerprint(fingerprint)
                    }
                }
            }
        )
    }
}
