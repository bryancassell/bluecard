package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How a badge's row looks with its progress bar, which semantics can't show: its colors, and
 * where it sits under the badge's name and Eagle-required line. Checked against reference
 * images in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same fixed screen and SDK as RequirementRowScreenshotTest, for the same reasons.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class BadgeRowScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun inProgress() {
        val rows = listOf(
            BadgeListItem(
                "camping",
                "Camping",
                EagleRequirement.Required,
                BadgeStatus.InProgress,
                fractionDone = 0.4f
            ),
            // Nothing done yet: the bar's track still shows its full length.
            BadgeListItem(
                "chess",
                "Chess",
                eagle = null,
                BadgeStatus.InProgress,
                fractionDone = 0f
            ),
            BadgeListItem("cooking", "Cooking", EagleRequirement.Required, BadgeStatus.Completed)
        )
        composeTestRule.setContent {
            BlueCardTheme {
                Column(Modifier.testTag(ROWS)) {
                    val listFormatter = rememberBadgeNameListFormatter()
                    rows.forEach { BadgeRow(it, listFormatter, onClick = {}) }
                }
            }
        }
        composeTestRule.onNodeWithTag(ROWS).captureRoboImage()
    }

    private companion object {
        const val ROWS = "rows"
    }
}
