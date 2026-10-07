package io.github.bryancassell.bluecard.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How Home's rank card looks, which semantics can't show: its colors, and the trail's dots, each
 * filled, partly filled or hollow. Checked against reference images in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same fixed screen and SDK as RequirementRowScreenshotTest, for the same reasons.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class RankCardScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun noRankEarned() = capture(earned = 0, fractionDone = 0.25f)

    @Test
    fun rankInProgress() = capture(earned = 2, fractionDone = 0.35f)

    @Test
    fun everyRankEarned() = capture(earned = 7)

    // Pale Blue, with Scouting America Blue for its quieter text and the ranks still to earn.
    @Test
    @Config(qualifiers = "+night")
    fun rankInProgress_darkMode() = capture(earned = 2, fractionDone = 0.35f)

    // The trail runs from right to left, as the rest of the card does.
    @Test
    fun rankInProgress_rightToLeft() =
        capture(earned = 2, fractionDone = 0.35f, layoutDirection = LayoutDirection.Rtl)

    /** The card with the first [earned] ranks earned and the next [fractionDone] done. */
    private fun capture(
        earned: Int,
        fractionDone: Float? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ) {
        val uiState = HomeUiState.Ready(
            name = "Sam Rivera",
            unitNumber = "214",
            ranks = ranks(earned, fractionDone),
            badges = ProgressCounts(completed = 0, inProgress = 0),
            eagle = ProgressCounts(completed = 0, inProgress = 0),
            eagleTotal = 13,
            badgesInProgress = emptyList()
        )
        composeTestRule.setContent {
            BlueCardTheme {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    RankCard(uiState, onOpenRank = {}, modifier = Modifier.testTag(CARD))
                }
            }
        }
        composeTestRule.onNodeWithTag(CARD).captureRoboImage()
    }

    private companion object {
        const val CARD = "card"
    }
}
