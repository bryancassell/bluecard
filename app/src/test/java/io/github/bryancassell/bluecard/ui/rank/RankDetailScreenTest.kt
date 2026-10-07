package io.github.bryancassell.bluecard.ui.rank

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.RankStatus
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.badge.RequirementItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class RankDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedRequirements = mutableListOf<String>()
    private val marks = mutableListOf<LocalDate>()
    private var unmarks = 0
    private var clears = 0
    private val saveFailuresShown = mutableListOf<TaskFailure>()
    private val today = LocalDate.of(2026, 5, 20)
    private var reportShareRequests = 0
    private var reportsShared = 0
    private val reportsSaved = mutableListOf<Uri>()
    private val reportFailuresShown = mutableListOf<TaskFailure>()

    /** The intents of the activities launched for a result, such as the file picker's. */
    private val launchedForResult = mutableListOf<Intent>()
    private val destination = Uri.parse("content://documents/tenderfoot-report.pdf")

    /**
     * Stands in for the activity's result registry, so the file picker gives [destination]
     * straight away, as the testing guide shows:
     * https://developer.android.com/training/basics/intents/result#test
     */
    private val resultRegistryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                launchedForResult += contract.createIntent(application, input)
                dispatchResult(requestCode, destination)
            }
        }
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private val ready = RankDetailUiState.Ready(
        name = "Tenderfoot",
        summary = "Our summary of Tenderfoot.",
        officialUrl = "https://www.scouting.org/tenderfoot.pdf",
        requirements = listOf(
            RequirementItem("1a", "Pack for a campout.", null, true, markedByHand = true),
            RequirementItem("1b", "Sleep in a tent.", null, false, markedByHand = true),
            RequirementItem("2a", "Cook a meal.", null, false, markedByHand = true)
        ),
        status = RankStatus.NotEarned
    )
    private val inProgress = ready.copy(status = RankStatus.InProgress, fractionDone = 0.4f)
    private val started = ready.copy(fractionDone = 0.25f, canClear = true, started = true)
    private val marked = ready.copy(
        status = RankStatus.Earned,
        earnedOnPriorDate = LocalDate.of(2025, 8, 1),
        canClear = true
    )
    private val earnedWithLife = ready.copy(status = RankStatus.Earned, earnedWith = "Life")
    private val earned = ready.copy(status = RankStatus.Earned, canClear = true)

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<RankDetailUiState>(RankDetailUiState.Loading)

    private fun show(state: RankDetailUiState) {
        uiState = state
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides resultRegistryOwner
            ) {
                RankDetailScreen(
                    uiState = uiState,
                    onOpenRequirement = { openedRequirements += it },
                    today = { today },
                    onMarkEarned = { marks += it },
                    onUnmarkEarned = { unmarks++ },
                    onShareReport = { reportShareRequests++ },
                    onReportShared = { reportsShared++ },
                    onSaveReport = { reportsSaved += it },
                    onReportFailureShown = { reportFailuresShown += it },
                    onClear = { clears++ },
                    onSaveFailureShown = { saveFailuresShown += it }
                )
            }
        }
    }

    private fun text(text: String) = composeTestRule.onNodeWithText(text)

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = text(summary).performScrollTo()

    /** A day in the date picker, such as "May 20, 2026". */
    private fun pickerDay(date: String) =
        composeTestRule.onNode(hasText(date, substring = true) and hasClickAction())

    private fun pickDay(date: String) {
        pickerDay(date).performClick()
        text("OK").performClick()
    }

    private val isSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private val anyProgressBar =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    private fun topOf(text: String) = text(text).fetchSemanticsNode().positionInRoot.y

    @Test
    fun loading_showsProgressOnly() {
        show(RankDetailUiState.Loading)

        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        text("Requirements").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(RankDetailUiState.LoadFailed)

        text("Couldn't load your data. Try closing and reopening BlueCard.").assertIsDisplayed()
        text("Requirements").assertDoesNotExist()
    }

    @Test
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        show(RankDetailUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = RankDetailUiState.LoadFailed }
    }

    @Test
    fun unavailable_showsMessageOnly() {
        show(RankDetailUiState.Unavailable)

        text("This rank's requirements aren't in this version of BlueCard.").assertIsDisplayed()
        text("Requirements").assertDoesNotExist()
    }

    @Test
    fun unavailable_isAnnouncedWhenItReplacesLoading() {
        show(RankDetailUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "This rank's requirements aren't in this version of BlueCard."
        ) { uiState = RankDetailUiState.Unavailable }
    }

    @Test
    fun ready_showsRank_withoutCounselorEagleLabelOrReport() {
        show(ready)

        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        text("Tenderfoot").assert(isHeading()).assertIsDisplayed()
        text("Our summary of Tenderfoot.").assertIsDisplayed()
        text("Requirements").performScrollTo().assert(isHeading())
        text("Counselor").assertDoesNotExist()
        text("Add counselor").assertDoesNotExist()
        text("Eagle-required").assertDoesNotExist()
        text("Share report").assertDoesNotExist()
    }

    @Test
    fun ready_showsRequirementsInOrder_eachOpeningItsPage() {
        show(ready)

        val summaries = listOf("Pack for a campout.", "Sleep in a tent.", "Cook a meal.")
        val tops = summaries.map(::topOf)
        summaries.forEach {
            row(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(
                    SemanticsMatcher("click label is \"open requirement\"") { node ->
                        node.config[SemanticsActions.OnClick].label == "open requirement"
                    }
                )
                .performClick()
        }

        assertEquals(tops.sorted(), tops)
        assertEquals(listOf("1a", "1b", "2a"), openedRequirements)
    }

    @Test
    fun officialLink_opensOfficialRequirements() {
        show(ready)

        text("Official requirements").performClick()

        val started = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.scouting.org/tenderfoot.pdf", started?.dataString)
    }

    // The status card says it's in progress, so screen readers don't hear it twice.
    @Test
    fun inProgress_showsHowMuchIsDone_andTheStatusCardSaysInProgress() {
        show(inProgress)

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f)))
            .assertIsDisplayed()
            .assert(hasStateDescription("40% done"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        text("In progress").performScrollTo().assertIsDisplayed()
    }

    // Only the lowest rank not earned is in progress.
    @Test
    fun startedButNotInProgress_showsHowMuchIsDone_withoutInProgress() {
        show(started)

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)))
            .assertIsDisplayed()
            .assert(hasStateDescription("25% done"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun withoutFractionDone_hasNoBar() {
        show(earned)

        composeTestRule.onNode(anyProgressBar).assertDoesNotExist()
    }

    @Test
    fun rankNotEarned_canBeMarkedEarned_onADayUpToToday() {
        show(ready)

        text("Mark earned").performScrollTo().performClick()
        // It opens at today.
        pickerDay("May 20, 2026").assert(isSelected)
        pickerDay("May 21, 2026").assertIsNotEnabled()
        pickDay("May 10, 2026")

        assertEquals(listOf(LocalDate.of(2026, 5, 10)), marks)
        text("OK").assertDoesNotExist()
    }

    @Test
    fun rankNotStarted_saysSo_andAsksWhetherItsAlreadyEarned_aboveMarkEarned() {
        show(ready)

        val tops = listOf("Not started", "Already earned Tenderfoot?", "Mark earned").map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun nextRankToEarn_saysInProgress() {
        show(inProgress)

        text("In progress").performScrollTo().assertIsDisplayed()
        text("Already earned Tenderfoot?").performScrollTo().assertIsDisplayed()
    }

    // Only the next rank to earn is in progress, as on Ranks.
    @Test
    fun startedRank_thatIsntNextToEarn_saysStarted() {
        show(started)

        text("Started").performScrollTo().assertIsDisplayed()
        text("In progress").assertDoesNotExist()
    }

    @Test
    fun markEarned_isBetweenOfficialLinkAndRequirements() {
        show(ready)

        val tops = listOf("Official requirements", "Mark earned", "Requirements").map(::topOf)

        assertEquals(tops.sorted(), tops)
    }

    // So a mistaken Unmark loses nothing.
    @Test
    fun markEarned_afterUnmarking_opensAtTheDateUnmarked() {
        show(ready.copy(unmarkedDate = LocalDate.of(2026, 4, 15)))

        text("Mark earned").performScrollTo().performClick()
        pickerDay("April 15, 2026").assert(isSelected)
        text("OK").performClick()

        assertEquals(listOf(LocalDate.of(2026, 4, 15)), marks)
    }

    @Test
    fun markEarned_cancelled_marksNothing() {
        show(ready)

        text("Mark earned").performScrollTo().performClick()
        text("Cancel").performClick()

        assertEquals(emptyList<LocalDate>(), marks)
    }

    @Test
    fun markedRank_showsItsDate_withChangeDateAndUnmark() {
        show(marked)

        text("Earned on Aug 1, 2025").performScrollTo().assertIsDisplayed()
        text("Mark earned").assertDoesNotExist()

        text("Change date").performScrollTo().performClick()
        pickerDay("August 1, 2025").assert(isSelected)
        pickDay("August 5, 2025")
        assertEquals(listOf(LocalDate.of(2025, 8, 5)), marks)

        text("Unmark").performScrollTo().performClick()
        assertEquals(1, unmarks)
    }

    @Test
    fun rankEarnedWithARankAbove_saysSo_andCanBeGivenADate() {
        show(earnedWithLife)

        text("Counted as earned with Life").performScrollTo().assertIsDisplayed()
        text("Mark earned").assertDoesNotExist()
        text("Unmark").assertDoesNotExist()

        text("Add date").performScrollTo().performClick()
        pickerDay("May 20, 2026").assert(isSelected)
        pickDay("May 10, 2026")

        assertEquals(listOf(LocalDate.of(2026, 5, 10)), marks)
        assertEquals(0, unmarks)
    }

    // As when it was marked earned itself, before it was unmarked.
    @Test
    fun rankEarnedWithARankAbove_addDate_afterUnmarking_opensAtTheDateUnmarked() {
        show(earnedWithLife.copy(unmarkedDate = LocalDate.of(2026, 4, 15)))

        text("Add date").performScrollTo().performClick()

        pickerDay("April 15, 2026").assert(isSelected)
    }

    @Test
    fun rankEarnedFromItsRequirements_saysWhen() {
        show(earned.copy(earnedOn = LocalDate.of(2026, 4, 15)))

        text("Earned on Apr 15, 2026").performScrollTo().assertIsDisplayed()
        text("Change date").assertDoesNotExist()
    }

    @Test
    fun rankEarnedFromItsRequirements_withoutADate_saysEarned() {
        show(earned)

        text("Earned").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rankWaitingOnTheRankBelow_saysSo_aboveMarkEarned() {
        show(ready.copy(fractionDone = 1f, waitingOn = "Scout"))

        val tops = listOf("Earned once Scout is earned", "Mark earned").map(::topOf)
        assertEquals(tops.sorted(), tops)
        // Waiting on Scout says why it isn't earned, in place of its status and the question.
        text("Started").assertDoesNotExist()
        text("Already earned Tenderfoot?").assertDoesNotExist()
        text("Mark earned").performScrollTo().performClick()
        pickDay("May 10, 2026")
        assertEquals(listOf(LocalDate.of(2026, 5, 10)), marks)
    }

    @Test
    fun rankNotWaiting_doesntSayWhatItsWaitingOn() {
        show(started)

        composeTestRule.onNode(hasText("Earned once", substring = true)).assertDoesNotExist()
    }

    @Test
    fun rankEarnedFromItsRequirements_cantBeMarked() {
        show(earned)

        text("Mark earned").assertDoesNotExist()
        text("Already earned Tenderfoot?").assertDoesNotExist()
        text("Add date").assertDoesNotExist()
        text("Unmark").assertDoesNotExist()
    }

    // With nothing between them, as there is for every other rank and on Badge detail.
    @Test
    fun rankEarnedFromItsRequirements_leavesRoomUnderTheOfficialLink() {
        show(earned)

        val link = text("Official requirements").getUnclippedBoundsInRoot()
        val heading = text("Requirements").getUnclippedBoundsInRoot()
        assertTrue(heading.top - link.bottom >= 16.dp)
    }

    private fun assertOffersReport() {
        listOf("Share report", "Save report").forEach {
            text(it)
                .performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertIsDisplayed()
        }
    }

    @Test
    fun earnedRank_offersToShareAndSaveItsReport() {
        show(earned.copy(earnedOn = LocalDate.of(2026, 4, 15)))

        assertOffersReport()
    }

    // It's earned, though nothing may be recorded for it.
    @Test
    fun rankEarnedWithARankAbove_offersItsReport() {
        show(earnedWithLife)

        assertOffersReport()
    }

    @Test
    fun rankNotEarned_hasNoReport_evenWithItsRequirementsComplete() {
        show(ready.copy(fractionDone = 1f, waitingOn = "Scout", canClear = true))

        text("Share report").assertDoesNotExist()
        text("Save report").assertDoesNotExist()
    }

    @Test
    fun reportButtons_areUnderHowTheRankIsEarned_aboveTheRequirements() {
        show(marked)

        val tops = listOf("Earned on Aug 1, 2025", "Change date", "Share report", "Requirements")
            .map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun shareReport_asksForTheReport() {
        show(marked)

        text("Share report").performScrollTo().performClick()

        assertEquals(1, reportShareRequests)
        // The share sheet opens once the report is ready.
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun reportToShare_opensShareSheetWithIt_once() {
        val report = Uri.parse("content://io.github.bryancassell.bluecard.reports/tenderfoot.pdf")
        show(marked.copy(reportToShare = report))
        composeTestRule.waitForIdle()

        val chooser = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val send =
            IntentCompat.getParcelableExtra(chooser!!, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(
            report,
            IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        )
        assertEquals(1, reportsShared)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun saveReport_suggestsTheRanksFileName_andSavesWhereTheScoutChose() {
        show(marked)

        text("Save report").performScrollTo().performClick()

        val picker = launchedForResult.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        assertEquals("Tenderfoot rank report.pdf", picker.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(listOf(destination), reportsSaved)
    }

    @Test
    fun reportFailed_showsMessage_thenReportsItShown() {
        val failure = TaskFailure()
        show(marked.copy(reportFailure = failure))

        val message = "Couldn't create the report. Try again."
        text(message).assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), reportFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        text(message).assertDoesNotExist()
        assertEquals(listOf(failure), reportFailuresShown)
    }

    @Test
    fun rankNotStarted_hasNoClearButton() {
        show(ready)

        text("Clear progress").assertDoesNotExist()
    }

    @Test
    fun clear_isLastOnThePage_asksFirst_thenClears() {
        show(started)

        val tops = listOf("Cook a meal.", "Clear progress").map(::topOf)
        assertEquals(tops.sorted(), tops)
        text("Clear progress").performScrollTo().performClick()
        text("Clear progress on Tenderfoot?").assertIsDisplayed()
        text("What you recorded for it will be removed.").assertIsDisplayed()
        assertEquals(0, clears)
        composeTestRule.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(1, clears)
        text("Clear progress on Tenderfoot?").assertDoesNotExist()
    }

    @Test
    fun clear_ofARankOthersCountOn_namesTheRanksThatWontCountAsEarned() {
        show(marked.copy(unearnedByClear = listOf("Scout", "Tenderfoot", "Second Class")))

        text("Clear progress").performScrollTo().performClick()

        text(
            "What you recorded for it will be removed, and Scout, Tenderfoot, and Second Class " +
                "will no longer count as earned."
        ).assertIsDisplayed()
    }

    @Test
    fun clear_cancel_clearsNothing() {
        show(started)

        text("Clear progress").performScrollTo().performClick()
        text("Cancel").performClick()

        assertEquals(0, clears)
        text("Clear progress on Tenderfoot?").assertDoesNotExist()
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        val failure = TaskFailure()
        show(started.copy(saveFailure = failure))

        val message = "Couldn't save. Try again."
        text(message).assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        text(message).assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }
}
