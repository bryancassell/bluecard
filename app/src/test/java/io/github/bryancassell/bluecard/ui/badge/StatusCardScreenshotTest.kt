package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import io.github.bryancassell.bluecard.ui.rank.RankDetailScreen
import io.github.bryancassell.bluecard.ui.rank.RankDetailUiState
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How a badge's or rank's status card looks, which semantics can't show: a card of the theme's
 * brightest surface, white in light mode, with a tonal Mark completed or Mark earned button and
 * its calendar icon. Checked against reference images in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The same fixed screen and SDK as RequirementRowScreenshotTest, for the same reasons.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class StatusCardScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val badge = BadgeDetailUiState.Ready(
        name = "Camping",
        summary = "Plan, pack and cook for campouts, and camp at least 20 nights.",
        eagle = EagleRequirement.Required,
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirements = listOf(
            RequirementItem("1", "Stay safe while camping.", null, true, markedByHand = true)
        )
    )

    @Test
    fun badgeInProgress() = capture(
        badge.copy(status = BadgeStatus.InProgress, fractionDone = 0.25f, canClear = true)
    )

    // Dark Blue, the dark scheme's brightest surface.
    @Test
    @Config(qualifiers = "+night")
    fun badgeInProgress_darkMode() = capture(
        badge.copy(status = BadgeStatus.InProgress, fractionDone = 0.25f, canClear = true)
    )

    @Test
    fun badgeMarkedCompleted() = capture(
        badge.copy(
            status = BadgeStatus.Completed,
            completedOnPriorDate = LocalDate.of(2025, 8, 1),
            completedOn = LocalDate.of(2025, 8, 1),
            canClear = true
        )
    )

    @Test
    fun rankWaitingOnTheRankBelow() {
        val rank = RankDetailUiState.Ready(
            name = "Tenderfoot",
            summary = "Camp out with your patrol and learn basic first aid.",
            officialUrl = "https://www.scouting.org/tenderfoot.pdf",
            requirements = listOf(
                RequirementItem(
                    "1a",
                    "Show your leader you're ready.",
                    null,
                    true,
                    markedByHand = true
                )
            ),
            status = RankStatus.NotEarned,
            fractionDone = 1f,
            waitingOn = "Scout",
            canClear = true
        )
        composeTestRule.setContent {
            BlueCardTheme {
                RankDetailScreen(
                    uiState = rank,
                    onOpenRequirement = {},
                    today = { TODAY },
                    onMarkEarned = {},
                    onUnmarkEarned = {},
                    onShareReport = {},
                    onReportShared = {},
                    onSaveReport = {},
                    onReportFailureShown = {},
                    onClear = {},
                    onSaveFailureShown = {}
                )
            }
        }
        composeTestRule.onNodeWithTag(STATUS_CARD_TAG).captureRoboImage()
    }

    /** The status card of Badge detail showing [uiState]. */
    private fun capture(uiState: BadgeDetailUiState) {
        composeTestRule.setContent {
            BlueCardTheme {
                BadgeDetailScreen(
                    uiState = uiState,
                    onOpenRequirement = {},
                    onEditCounselor = {},
                    today = { TODAY },
                    onMarkCompleted = {},
                    onUnmarkCompleted = {},
                    onShareReport = {},
                    onReportShared = {},
                    onSaveReport = {},
                    onReportFailureShown = {},
                    onClear = {},
                    onSaveFailureShown = {}
                )
            }
        }
        composeTestRule.onNodeWithTag(STATUS_CARD_TAG).captureRoboImage()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 5, 20)
    }
}
