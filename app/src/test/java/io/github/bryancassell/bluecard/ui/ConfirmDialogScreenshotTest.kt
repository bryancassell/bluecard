package io.github.bryancassell.bluecard.ui

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How the dialog that confirms removing something looks: its confirm button is red, which
 * semantics can't show. Checked against a reference image in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same fixed screen and SDK as RequirementRowScreenshotTest, for the same reasons.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class ConfirmDialogScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun confirmIsRed() {
        composeTestRule.setContent {
            BlueCardTheme {
                ConfirmDialog(
                    title = "Clear progress on requirement 4b?",
                    message = "What you recorded for it will be removed.",
                    confirmLabel = "Clear",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNode(isDialog()).captureRoboImage()
    }
}
