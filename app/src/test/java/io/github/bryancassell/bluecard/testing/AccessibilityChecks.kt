package io.github.bryancassell.bluecard.testing

import android.os.Build
import android.view.View
import androidx.compose.ui.test.ComposeAccessibilityValidator
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.robolectric.annotation.GraphicsMode
import org.robolectric.config.ConfigurationRegistry
import org.robolectric.shadows.ShadowBuild

/**
 * A screen tall enough for Material's date picker to give each day a 48dp touch target, for a
 * test that opens it with the accessibility checks on. On Robolectric's default screen
 * (320x470dp), shorter than a phone's, the picker's days are 47dp tall and fail the checks.
 */
const val DATE_PICKER_SCREEN = SMALL_PHONE

/**
 * Runs Google's Accessibility Test Framework (ATF) checks before every action the test performs
 * (click, scroll, text input and so on), on every window, dialogs included. They catch problems
 * such as a control with no label for screen readers or a touch target smaller than 48dp. A
 * problem fails the test with ATF's `AccessibilityViewCheckException`.
 *
 * Compose's own `enableAccessibilityChecks()` checks nothing under Robolectric: ATF sees
 * `Build.FINGERPRINT` is "robolectric" and looks only at Views, never at composables
 * ([ATF #40](https://github.com/google/Accessibility-Test-Framework-for-Android/issues/40)). So
 * this gives ATF another fingerprint while it checks, and only then, because Compose's test code
 * reads it too. ATF also finds nothing under Robolectric's default graphics, so the test class
 * needs `@GraphicsMode(NATIVE)`. `AccessibilityChecksTest` fails if an update stops the checks
 * finding problems.
 */
// setComposeAccessibilityValidator is the hook enableAccessibilityChecks() uses. It's public but
// restricted to Compose's own libraries, so a Compose update could change it.
@Suppress("RestrictedApi")
fun ComposeTestRule.enableAccessibilityChecksUnderRobolectric() {
    check(ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.NATIVE) {
        "Accessibility checks find nothing without @GraphicsMode(NATIVE)"
    }
    val validator = AccessibilityValidator().setRunChecksFromRootView(true)
    (this as AndroidComposeTestRule<*, *>).setComposeAccessibilityValidator(
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
