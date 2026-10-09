package io.github.bryancassell.bluecard.ui.ranks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.testing.hasLine
import io.github.bryancassell.bluecard.testing.onReadAsOne
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RanksScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private val openedRanks = mutableListOf<String>()

    // Scout is earned, Tenderfoot is in progress, Second Class is started out of order, and the
    // rest aren't started.
    private val ranks = listOf(
        RankListItem("scout", "Scout", RankStatus.Earned),
        RankListItem("tenderfoot", "Tenderfoot", RankStatus.InProgress, fractionDone = 0.4f),
        RankListItem("second-class", "Second Class", RankStatus.NotEarned, fractionDone = 0.25f),
        RankListItem("first-class", "First Class", RankStatus.NotEarned),
        RankListItem("star", "Star", RankStatus.NotEarned),
        RankListItem("life", "Life", RankStatus.NotEarned),
        RankListItem("eagle", "Eagle Scout", RankStatus.NotEarned)
    )

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<RanksUiState>(RanksUiState.Loading)

    private fun show(state: RanksUiState, density: Density? = null) {
        uiState = state
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides (density ?: LocalDensity.current)) {
                RanksScreen(uiState = uiState, onOpenRank = { openedRanks += it })
            }
        }
    }

    // Each row merges its texts, so a row is the node with the rank's name.
    // A row is read as one, with a label of its own, so it's found by the name it shows.
    private fun row(name: String) = composeTestRule.onReadAsOne(name)

    private fun list() = composeTestRule.onNode(hasScrollToNodeAction())

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    private val anyProgressBar =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun stateDescription(description: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)

    @Test
    fun loading_showsTitleAndProgressOnly() {
        show(RanksUiState.Loading)

        composeTestRule.onNodeWithText("Ranks").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        list().assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsTitleAndMessage() {
        show(RanksUiState.LoadFailed)

        composeTestRule.onNodeWithText("Ranks").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        list().assertDoesNotExist()
    }

    @Test
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        show(RanksUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = RanksUiState.LoadFailed }
    }

    @Test
    fun ready_showsRanksInGivenOrder() {
        show(RanksUiState.Ready(ranks))

        composeTestRule.onNodeWithText("Ranks").assert(isHeading())
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        // As screen readers read them: each row's lines in turn.
        val rows = list().onChildren()
        rows[0].assertContentDescriptionEquals("Scout. Earned")
        rows[1].assertContentDescriptionEquals("Tenderfoot. In progress")
        rows[2].assertContentDescriptionEquals("Second Class")
        rows[3].assertContentDescriptionEquals("First Class")
    }

    @Test
    fun rows_areButtonsThatOpenTheRank() {
        show(RanksUiState.Ready(ranks))

        row("Star")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label is \"open rank\"") {
                    it.config[SemanticsActions.OnClick].label == "open rank"
                }
            )
    }

    @Test
    fun clickingRank_opensIt() {
        show(RanksUiState.Ready(ranks))

        row("Tenderfoot").performClick()

        assertEquals(listOf("tenderfoot"), openedRanks)
    }

    @Test
    fun earnedRank_isLabeledEarned() {
        show(RanksUiState.Ready(ranks))

        row("Scout").assert(hasLine("Earned"))
        composeTestRule.onAllNodes(hasText("Earned"), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun inProgressRank_isTheOnlyOneLabeledInProgress() {
        show(RanksUiState.Ready(ranks))

        row("Tenderfoot").assert(hasLine("In progress"))
        composeTestRule.onAllNodes(hasText("In progress"), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun ranksWithABar_readHowMuchIsDone_asTheRowsState() {
        show(RanksUiState.Ready(ranks))

        row("Tenderfoot").assert(stateDescription("40% done"))
        // Started out of order, without the In progress label.
        row("Second Class").assert(stateDescription("25% done"))
        for (name in listOf("Scout", "First Class", "Star")) {
            row(name).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        }
    }

    @Test
    fun ranksWithABar_showHowMuchIsDone() {
        show(RanksUiState.Ready(ranks))

        composeTestRule.onAllNodes(anyProgressBar, useUnmergedTree = true).assertCountEquals(2)
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f)),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    // A rank whose requirements are all complete waits on the rank below it, so it can be done
    // without being earned.
    @Test
    fun rankAllDone_butNotEarned_readsAHundredPercent_withoutALabel() {
        val waiting = RankListItem("star", "Star", RankStatus.NotEarned, fractionDone = 1f)
        show(RanksUiState.Ready(listOf(waiting)))

        row("Star").assert(stateDescription("100% done")).assertContentDescriptionEquals("Star")
        composeTestRule.onNodeWithText("Earned", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("In progress", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun atLargestFontSize_scrollsToTheLastRank() {
        show(RanksUiState.Ready(ranks), density = Density(1f, fontScale = 2f))

        list().performScrollToNode(hasContentDescription("Eagle Scout"))

        row("Eagle Scout").assertIsDisplayed().performClick()
        assertEquals(listOf("eagle"), openedRanks)
    }
}
