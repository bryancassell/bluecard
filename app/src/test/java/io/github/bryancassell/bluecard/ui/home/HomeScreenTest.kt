package io.github.bryancassell.bluecard.ui.home

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.testing.hasClickLabel
import io.github.bryancassell.bluecard.testing.hasLine
import io.github.bryancassell.bluecard.testing.hasNoLineWith
import io.github.bryancassell.bluecard.testing.readWithOnlyItsLastLineShown
import io.github.bryancassell.bluecard.testing.turnOnScreenReader
import io.github.bryancassell.bluecard.testing.visualText
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private val openedBadges = mutableListOf<String>()
    private val openedRanks = mutableListOf<String>()
    private var badgesOpened = 0
    private var ranksOpened = 0
    private var dataManagementOpened = 0

    private val noProgress = HomeUiState.Ready(
        name = "Alex Scout",
        unitNumber = "123",
        ranks = ranks(earned = 0),
        badges = ProgressCounts(completed = 0, inProgress = 0),
        eagle = ProgressCounts(completed = 0, inProgress = 0),
        eagleTotal = 13,
        badgesInProgress = emptyList()
    )

    private val withProgress = noProgress.copy(
        ranks = ranks(earned = 2, fractionDone = 0.4f),
        badges = ProgressCounts(completed = 5, inProgress = 3),
        eagle = ProgressCounts(completed = 4, inProgress = 2)
    )

    private fun inProgress(id: String, name: String, eagle: EagleRequirement?) = BadgeListItem(
        id = id,
        name = name,
        eagle = eagle,
        status = BadgeStatus.InProgress,
        fractionDone = 0.25f
    )

    private val withBadgesInProgress = withProgress.copy(
        badgesInProgress = listOf(
            inProgress("camping", "Camping", EagleRequirement.Required),
            inProgress("chess", "Chess", eagle = null),
            inProgress(
                "hiking",
                "Hiking",
                EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming"))
            )
        )
    )

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)

    /** The view [show] composes into. */
    private lateinit var view: View

    private fun show(state: HomeUiState) {
        uiState = state
        composeTestRule.setContent {
            view = LocalView.current
            HomeScreen(
                uiState = uiState,
                onOpenBadge = { openedBadges += it },
                onOpenBadges = { badgesOpened++ },
                onOpenRanks = { ranksOpened++ },
                onOpenRank = { openedRanks += it },
                onOpenDataManagement = { dataManagementOpened++ }
            )
        }
    }

    private fun text(text: String, substring: Boolean = false) =
        composeTestRule.onNodeWithText(text, substring)

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    private fun eagleBar(fraction: Float) = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo(fraction, 0f..1f)
    )

    private val hiddenFromScreenReaders =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    private val opensBadge = hasClickLabel("open badge")

    // Found in the unmerged tree by the name it shows: it's read as one, with a label of its own.
    private fun row(name: String) = composeTestRule.onNode(
        opensBadge and hasAnyDescendant(hasText(name)),
        useUnmergedTree = true
    )

    private val opensRank = hasClickLabel("open rank")

    private fun rankCard() =
        composeTestRule.onNode(hasContentDescription("Your rank", substring = true) and isHeading())

    /**
     * The rank card in the unmerged tree, which keeps the card's parts although screen readers
     * don't get them.
     */
    private val isRankCard = isHeading() and hasAnyDescendant(hasText("Your rank"))

    /** Text drawn on the rank card. */
    private fun drawn(text: String) = composeTestRule
        .onNode(hasText(text) and hasAnyAncestor(isRankCard), useUnmergedTree = true)

    @Test
    fun loading_showsProgressAndNoProfile() {
        show(HomeUiState.Loading)

        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        text("Merit badges").assertDoesNotExist()
        text("Ranks").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(HomeUiState.LoadFailed)

        text("Couldn't load your data. Try closing and reopening BlueCard.").assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        text("Merit badges").assertDoesNotExist()
        text("Ranks").assertDoesNotExist()
    }

    @Test
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        show(HomeUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = HomeUiState.LoadFailed }
    }

    @Test
    fun ready_showsNameAsHeadingAndUnit() {
        show(noProgress)

        text("Alex Scout").assert(isHeading()).assertIsDisplayed()
        text("Unit: 123").assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
    }

    // Home is laid out left-to-right, like the English strings, but a name typed in Persian
    // keeps its own direction: its final period is drawn at its end, which is on its left.
    @Test
    fun nameTypedInPersian_keepsItsPunctuationAtItsEnd() {
        show(noProgress.copy(name = "علی رضایی."))

        // The rank's card is a heading too.
        val name = composeTestRule.onNode(isHeading() and hasText("علی", substring = true))
            .visualText()

        assertTrue(name, name.startsWith("."))
    }

    // The same inside an English string.
    @Test
    fun unitNumberTypedInPersian_keepsItsPunctuationAtItsEnd() {
        show(noProgress.copy(unitNumber = "گروه ۱۲۳."))

        val unit = text("Unit:", substring = true).visualText()

        assertTrue(unit, unit.startsWith("Unit: ."))
    }

    @Test
    fun noRankEarned_saysSo_withScoutNext() {
        show(noProgress)

        rankCard()
            .assert(
                hasContentDescription(
                    "Your rank. None yet. 0 of 7 ranks earned. Next: Scout. In progress"
                )
            )
            .assert(hasStateDescription("0% done"))
            .assertIsDisplayed()
        drawn("None yet").assertIsDisplayed()
        drawn("Next: Scout").assertIsDisplayed()
        drawn("In progress").assertIsDisplayed()
    }

    // Screen readers hear the card as one heading: the rank, how many ranks are earned, and the
    // rank in progress, with how much of it is done as its state, as on Ranks.
    @Test
    fun rankEarned_showsIt_withTheRankInProgress() {
        show(withProgress)

        rankCard()
            .assert(
                hasContentDescription(
                    "Your rank. Tenderfoot. 2 of 7 ranks earned. Next: Second Class. In progress"
                )
            )
            .assert(hasStateDescription("40% done"))
            .assertIsDisplayed()
        drawn("Your rank").assertIsDisplayed()
        drawn("Tenderfoot").assertIsDisplayed()
        drawn("Next: Second Class").assertIsDisplayed()
        drawn("In progress").assertIsDisplayed()
    }

    // The names under the trail's ends would read as ranks of their own.
    @Test
    fun trailsEndNames_arentRead() {
        show(withProgress)

        rankCard().assert(!hasContentDescription("Scout", substring = true))
        composeTestRule.onAllNodes(hasText("Scout") or hasText("Eagle Scout")).assertCountEquals(0)
    }

    // As for a rank whose requirements version isn't in the catalog.
    @Test
    fun rankInProgress_thatCantBeMeasured_hasNoState() {
        show(withProgress.copy(ranks = ranks(earned = 2, fractionDone = null)))

        rankCard()
            .assert(hasContentDescription("Next: Second Class", substring = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun everyRankEarned_saysSo_andOpensNothing() {
        show(withProgress.copy(ranks = ranks(earned = 7)))

        rankCard()
            .assert(
                hasContentDescription(
                    "Your rank. Eagle Scout. 7 of 7 ranks earned. Every rank earned"
                )
            )
            .assert(!hasClickAction())
            .assertIsDisplayed()
        drawn("Every rank earned").assertIsDisplayed()
        drawn("In progress").assertDoesNotExist()
        composeTestRule.onAllNodes(opensRank).assertCountEquals(0)
    }

    // As in a catalog without ranks: Home shows the rest rather than failing.
    @Test
    fun noRanks_showsNoRankCard() {
        show(withProgress.copy(ranks = emptyList()))

        rankCard().assertDoesNotExist()
        text("Your merit badges").assertIsDisplayed()
    }

    @Test
    fun rankCard_isAboveTheBadgeSummary_inset() {
        show(withProgress)

        val screen = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val unit = text("Unit: 123").getUnclippedBoundsInRoot()
        val card = rankCard().getUnclippedBoundsInRoot()
        val summary = text("Your merit badges").getUnclippedBoundsInRoot()
        assertTrue(unit.bottom < card.top)
        assertTrue(card.bottom < summary.top)
        assertTrue(card.left > screen.left)
        assertTrue(card.right < screen.right)
    }

    /** What TalkBack reads as the rank card with only its [lastLine] on screen. */
    private fun readRankCardWithOnlyItsLastLineShown(lastLine: String) =
        composeTestRule.readWithOnlyItsLastLineShown(view, isRankCard, "Your rank", lastLine)

    @Test
    fun rankCard_partlyScrolledOff_isReadWhole() {
        turnOnScreenReader()
        show(withBadgesInProgress)

        assertEquals(
            "Your rank. Tenderfoot. 2 of 7 ranks earned. Next: Second Class. In progress",
            readRankCardWithOnlyItsLastLineShown("Next: Second Class")
        )
    }

    // Not clickable, so nothing merges into it.
    @Test
    fun everyRankEarned_partlyScrolledOff_isReadWhole() {
        turnOnScreenReader()
        show(withBadgesInProgress.copy(ranks = ranks(earned = 7)))

        assertEquals(
            "Your rank. Eagle Scout. 7 of 7 ranks earned. Every rank earned",
            readRankCardWithOnlyItsLastLineShown("Every rank earned")
        )
    }

    @Test
    fun clickingRankCard_opensTheRankInProgress() {
        show(withProgress)

        rankCard()
            .assert(opensRank)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(listOf("second-class"), openedRanks)
        assertEquals(0, ranksOpened)
    }

    @Test
    fun noProgress_showsMessageAndNoSummary() {
        show(noProgress)

        text("You haven't started any merit badges yet.").assertIsDisplayed()
        text("Your merit badges").assertDoesNotExist()
        text("Eagle-required").assertDoesNotExist()
    }

    @Test
    fun withProgress_showsBadgeCounts() {
        show(withProgress)

        text("You haven't started any merit badges yet.").assertDoesNotExist()
        text("Your merit badges").assert(isHeading()).assertIsDisplayed()
        text("5 completed").assertIsDisplayed()
        text("3 in progress").assertIsDisplayed()
    }

    @Test
    fun withProgress_showsEagleProgress() {
        show(withProgress)

        // Below the rank card, so it can start off screen.
        text("Eagle-required").assert(isHeading()).performScrollTo().assertIsDisplayed()
        text("4 of 13 completed").performScrollTo().assertIsDisplayed()
        text("2 in progress").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun eagleProgressBar_showsShareCompleted_hiddenFromScreenReaders() {
        show(withProgress)

        // Screen readers get "4 of 13 completed" instead of a percentage without context.
        composeTestRule.onNode(eagleBar(4f / 13))
            .performScrollTo()
            .assertIsDisplayed()
            .assert(hiddenFromScreenReaders)
    }

    @Test
    fun withProgress_noEagleBadgesInCatalog_hidesEagleProgress() {
        show(
            withProgress.copy(
                eagle = ProgressCounts(completed = 0, inProgress = 0),
                eagleTotal = 0
            )
        )

        text("Your merit badges").assertIsDisplayed()
        text("Eagle-required").assertDoesNotExist()
    }

    @Test
    fun badgesInProgress_areListedInGivenOrder_betweenSummaryAndButtons() {
        show(withBadgesInProgress)

        // Unclipped, since some of these are scrolled out of view.
        val eagleCard = text("4 of 13 completed").getUnclippedBoundsInRoot()
        val camping = row("Camping").getUnclippedBoundsInRoot()
        val chess = row("Chess").getUnclippedBoundsInRoot()
        val hiking = row("Hiking").getUnclippedBoundsInRoot()
        val badgesButton = text("Merit badges").getUnclippedBoundsInRoot()
        assertTrue(eagleCard.bottom < camping.top)
        assertTrue(camping.bottom <= chess.top)
        assertTrue(chess.bottom <= hiking.top)
        assertTrue(hiking.bottom < badgesButton.top)
    }

    @Test
    fun badgesInProgress_runEdgeToEdge_asOnBadges() {
        show(withBadgesInProgress)

        // Unclipped, since the row may be scrolled out of view.
        val screen = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val chess = row("Chess").getUnclippedBoundsInRoot()
        assertEquals(screen.left, chess.left)
        assertEquals(screen.right, chess.right)
        // Everything else keeps its inset.
        assertTrue(text("Merit badges").getUnclippedBoundsInRoot().left > screen.left)
    }

    @Test
    fun badgesInProgress_areLabeledAsOnBadges() {
        show(withBadgesInProgress)

        row("Camping").assert(hasLine("Eagle-required")).assert(hasLine("In progress"))
        row("Chess")
            .assert(hasNoLineWith("Eagle-required"))
            .assert(hasLine("In progress"))
        row("Hiking")
            .assert(hasLine("Eagle-required (one of Cycling, Hiking, and Swimming)"))
            .assert(hasLine("In progress"))
    }

    @Test
    fun badgesInProgress_showHowMuchIsDoneAsOnBadges() {
        show(withBadgesInProgress)

        // Each row has a bar, and reads it as its state.
        composeTestRule
            .onAllNodes(
                hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)),
                useUnmergedTree = true
            )
            .assertCountEquals(3)
        for (name in listOf("Camping", "Chess", "Hiking")) {
            row(name).assert(hasStateDescription("25% done"))
        }
    }

    @Test
    fun badgesInProgress_areAListForScreenReaders() {
        show(withBadgesInProgress)

        val list = composeTestRule.onNode(
            SemanticsMatcher("is a list of 3 rows") {
                val info = it.config.getOrNull(SemanticsProperties.CollectionInfo)
                info?.rowCount == 3 && info.columnCount == 1
            }
        )
        list.onChildren().assertCountEquals(3)
        list.onChildren().assertAll(opensBadge)
    }

    @Test
    fun badgesInProgress_areButtons() {
        show(withBadgesInProgress)

        row("Chess").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun clickingBadgeInProgress_opensIt() {
        show(withBadgesInProgress)

        row("Chess").performScrollTo().performClick()

        assertEquals(listOf("chess"), openedBadges)
        assertEquals(0, badgesOpened)
    }

    @Test
    fun noBadgesInProgress_listsNoBadges() {
        // Such as when every badge the scout started is completed.
        show(withProgress.copy(badges = ProgressCounts(completed = 5, inProgress = 0)))

        composeTestRule.onAllNodes(opensBadge).assertCountEquals(0)
        text("Your merit badges").assertIsDisplayed()
    }

    @Test
    fun meritBadgesButton_opensBadges() {
        show(withProgress)

        text("Merit badges").performScrollTo().performClick()

        assertEquals(1, badgesOpened)
        assertEquals(0, ranksOpened)
        assertEquals(0, dataManagementOpened)
    }

    @Test
    fun ranksButton_opensRanks() {
        show(withProgress)

        text("Ranks").performScrollTo().performClick()

        assertEquals(1, ranksOpened)
        assertEquals(0, badgesOpened)
        assertEquals(0, dataManagementOpened)
    }

    // It's shown before the scout has started anything, as Merit badges is.
    @Test
    fun noProgress_offersRanks() {
        show(noProgress)

        text("Ranks").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manageDataButton_opensDataManagement() {
        show(withProgress)

        text("Manage data").performScrollTo().performClick()

        assertEquals(1, dataManagementOpened)
        assertEquals(0, badgesOpened)
        assertEquals(0, ranksOpened)
    }
}
