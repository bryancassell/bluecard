package io.github.bryancassell.bluecard.ui.home

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
import io.github.bryancassell.bluecard.testing.visualText
import io.github.bryancassell.bluecard.ui.badges.BadgeListItem
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedBadges = mutableListOf<String>()
    private var badgesOpened = 0
    private var dataManagementOpened = 0

    private val noProgress = HomeUiState.Ready(
        name = "Alex Scout",
        unitNumber = "123",
        badges = ProgressCounts(completed = 0, inProgress = 0),
        eagle = ProgressCounts(completed = 0, inProgress = 0),
        eagleTotal = 13,
        badgesInProgress = emptyList()
    )

    private val withProgress = noProgress.copy(
        badges = ProgressCounts(completed = 5, inProgress = 3),
        eagle = ProgressCounts(completed = 4, inProgress = 2)
    )

    private fun inProgress(id: String, name: String, eagle: EagleRequirement?) =
        BadgeListItem(id = id, name = name, eagle = eagle, status = BadgeStatus.InProgress)

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

    private fun show(uiState: HomeUiState) {
        composeTestRule.setContent {
            HomeScreen(
                uiState = uiState,
                onOpenBadge = { openedBadges += it },
                onOpenBadges = { badgesOpened++ },
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

    private val opensBadge = SemanticsMatcher("click label is \"open badge\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "open badge"
    }

    private fun row(name: String) = composeTestRule.onNode(hasText(name) and opensBadge)

    @Test
    fun loading_showsProgressAndNoProfile() {
        show(HomeUiState.Loading)

        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        text("Merit badges").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(HomeUiState.LoadFailed)

        text("Couldn't load your data. Try closing and reopening BlueCard.").assertIsDisplayed()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        text("Merit badges").assertDoesNotExist()
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

        val name = composeTestRule.onNode(isHeading()).visualText()

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

        text("Eagle-required").assert(isHeading()).assertIsDisplayed()
        text("4 of 13 completed").assertIsDisplayed()
        text("2 in progress").assertIsDisplayed()
    }

    @Test
    fun eagleProgressBar_showsShareCompleted_hiddenFromScreenReaders() {
        show(withProgress)

        // Screen readers get "4 of 13 completed" instead of a percentage without context.
        composeTestRule.onNode(eagleBar(4f / 13))
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

        row("Camping").assert(hasText("Eagle-required")).assert(hasText("In progress"))
        row("Chess")
            .assert(!hasText("Eagle-required", substring = true))
            .assert(hasText("In progress"))
        row("Hiking")
            .assert(hasText("Eagle-required (one of Cycling, Hiking, and Swimming)"))
            .assert(hasText("In progress"))
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
        assertEquals(0, dataManagementOpened)
    }

    @Test
    fun manageDataButton_opensDataManagement() {
        show(withProgress)

        text("Manage data").performScrollTo().performClick()

        assertEquals(1, dataManagementOpened)
        assertEquals(0, badgesOpened)
    }
}
