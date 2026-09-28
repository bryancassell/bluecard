package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class BadgesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedBadges = mutableListOf<String>()

    private val badges = listOf(
        BadgeListItem("camping", "Camping", eagleRequired = true, BadgeStatus.Completed),
        BadgeListItem("chess", "Chess", eagleRequired = false, BadgeStatus.InProgress),
        BadgeListItem("cooking", "Cooking", eagleRequired = true, BadgeStatus.NotStarted),
        BadgeListItem(
            "hiking",
            "Hiking",
            eagleRequired = true,
            BadgeStatus.NotStarted,
            eagleGroup = listOf("Cycling", "Hiking", "Swimming")
        )
    )

    private fun show(uiState: BadgesUiState) {
        composeTestRule.setContent {
            BadgesScreen(uiState = uiState, onOpenBadge = { openedBadges += it })
        }
    }

    // Each row merges its texts, so a row is the node with the badge's name.
    private fun row(name: String) = composeTestRule.onNodeWithText(name)

    private fun list() = composeTestRule.onNode(hasScrollToNodeAction())

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    @Test
    fun loading_showsProgressAndNoBadges() {
        show(BadgesUiState.Loading)

        composeTestRule.onNodeWithText("Merit badges").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        list().assertDoesNotExist()
    }

    @Test
    fun ready_showsBadgesInGivenOrder() {
        show(BadgesUiState.Ready(badges))

        composeTestRule.onNodeWithText("Merit badges").assert(isHeading())
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        val rows = list().onChildren()
        rows[0].assert(hasText("Camping"))
        rows[1].assert(hasText("Chess"))
        rows[2].assert(hasText("Cooking"))
        rows[3].assert(hasText("Hiking"))
    }

    @Test
    fun rows_areButtonsThatOpenTheBadge() {
        show(BadgesUiState.Ready(badges))

        row("Chess")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label is \"open badge\"") {
                    it.config[SemanticsActions.OnClick].label == "open badge"
                }
            )
    }

    @Test
    fun eagleRequiredBadges_areLabeled() {
        show(BadgesUiState.Ready(badges))

        row("Camping").assert(hasText("Eagle-required"))
        row("Cooking").assert(hasText("Eagle-required"))
        row("Chess").assert(!hasText("Eagle-required"))
    }

    @Test
    fun eagleGroupBadge_namesTheGroup() {
        show(BadgesUiState.Ready(badges))

        row("Hiking").assert(hasText("Eagle-required (one of Cycling, Hiking, Swimming)"))
        row("Hiking").assert(!hasText("Eagle-required"))
    }

    @Test
    fun completedBadge_isLabeledCompleted() {
        show(BadgesUiState.Ready(badges))

        row("Camping").assert(hasText("Completed"))
        row("Camping").assert(!hasText("In progress"))
    }

    @Test
    fun inProgressBadge_isLabeledInProgress() {
        show(BadgesUiState.Ready(badges))

        row("Chess").assert(hasText("In progress"))
        row("Chess").assert(!hasText("Completed"))
    }

    @Test
    fun notStartedBadge_hasNoStatusLabel() {
        show(BadgesUiState.Ready(badges))

        row("Cooking").assert(!hasText("In progress"))
        row("Cooking").assert(!hasText("Completed"))
    }

    @Test
    fun clickingBadge_opensIt() {
        show(BadgesUiState.Ready(badges))

        row("Chess").performClick()

        assertEquals(listOf("chess"), openedBadges)
    }

    @Test
    fun longList_scrollsToLastBadge() {
        // More badges than the full catalog (about 140).
        val many = (1..200).map {
            BadgeListItem("badge-$it", "Badge $it", eagleRequired = false, BadgeStatus.NotStarted)
        }
        show(BadgesUiState.Ready(many))
        // Rows off screen aren't composed until scrolled to.
        row("Badge 200").assertDoesNotExist()

        list().performScrollToNode(hasText("Badge 200"))

        row("Badge 200").assertIsDisplayed()
        row("Badge 200").performClick()
        assertEquals(listOf("badge-200"), openedBadges)
    }
}
