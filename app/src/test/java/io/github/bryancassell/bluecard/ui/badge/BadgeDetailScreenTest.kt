package io.github.bryancassell.bluecard.ui.badge

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentDialog
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.testing.AccessibilityChecks
import io.github.bryancassell.bluecard.testing.assertAnnouncedWhenShown
import io.github.bryancassell.bluecard.testing.visualText
import io.github.bryancassell.bluecard.ui.TaskFailure
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BadgeDetailScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createComposeRule()

    @get:Rule(order = 1)
    val accessibilityChecks = AccessibilityChecks(composeTestRule)

    private val openedRequirements = mutableListOf<String>()
    private var counselorEdits = 0
    private val marks = mutableListOf<LocalDate>()
    private var unmarks = 0

    /** What the page reads as today when the picker opens, which a test can move on. */
    private var today = LocalDate.of(2026, 5, 20)
    private var reportShareRequests = 0
    private var reportsShared = 0
    private val reportsSaved = mutableListOf<Uri>()
    private val reportFailuresShown = mutableListOf<TaskFailure>()
    private var clears = 0
    private val saveFailuresShown = mutableListOf<TaskFailure>()

    /** The intents of the activities launched for a result, such as the file picker's. */
    private val launchedForResult = mutableListOf<Intent>()

    /** What the file picker gives back for the file name it's given, or throws. */
    private var pickDestination: (fileName: String) -> Uri? = { destination }
    private val destination = Uri.parse("content://documents/camping-report.pdf")

    /**
     * Stands in for the activity's result registry, so the file picker gives its result
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
                dispatchResult(requestCode, pickDestination(input as String))
            }
        }
    }

    private val ready = BadgeDetailUiState.Ready(
        name = "Camping",
        summary = "Our summary of Camping.",
        eagle = EagleRequirement.Required,
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirements = listOf(
            RequirementItem("1", "Plan a campout.", null, true, markedByHand = true),
            RequirementItem(
                "2",
                "Do two of these.",
                Choice(2, 3),
                false,
                markedByHand = false
            ),
            RequirementItem(
                "3",
                "Keep a camping log.",
                null,
                false,
                markedByHand = false,
                tracker = TrackerCount(8, 12, "nights")
            ),
            RequirementItem("4", "Do all of these.", null, true, markedByHand = false)
        )
    )

    /** The UI state shown, which a test can change after [show]. */
    private var uiState by mutableStateOf<BadgeDetailUiState>(BadgeDetailUiState.Loading)

    private fun show(
        state: BadgeDetailUiState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ) {
        uiState = state
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides layoutDirection,
                LocalActivityResultRegistryOwner provides resultRegistryOwner
            ) {
                BadgeDetailScreen(
                    uiState = uiState,
                    onOpenRequirement = { openedRequirements += it },
                    onEditCounselor = { counselorEdits++ },
                    today = { today },
                    onMarkCompleted = { marks += it },
                    onUnmarkCompleted = { unmarks++ },
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

    /** The screen showing [uiState], for tests that only look at it. */
    @Composable
    private fun NoActionsBadgeDetailScreen(uiState: BadgeDetailUiState) {
        BadgeDetailScreen(
            uiState,
            onOpenRequirement = {},
            onEditCounselor = {},
            today = { today },
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

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

    /**
     * Where the text's node starts down the page. Every part of the page is laid out, on screen
     * or not, so these give their order.
     */
    private fun topOf(text: String) =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    /** A day in the date picker, such as "May 20, 2026". */
    private fun pickerDay(date: String) =
        composeTestRule.onNode(hasText(date, substring = true) and hasClickAction())

    private val isSelected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private val counselor = Counselor("Pat Lee", "+1 555-0100", "pat@example.com")

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is \"$label\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()

    private fun startedActivity(): Intent? = shadowOf(application).nextStartedActivity

    private val loadingIndicator = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo.Indeterminate
    )

    @Test
    fun loading_showsProgressOnly() {
        show(BadgeDetailUiState.Loading)

        composeTestRule.onNode(loadingIndicator).assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirements").assertDoesNotExist()
    }

    @Test
    fun ready_showsBadge() {
        show(ready)

        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
        composeTestRule.onNodeWithText("Camping").assert(isHeading()).assertIsDisplayed()
        composeTestRule.onNodeWithText("Our summary of Camping.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirements").performScrollTo().assert(isHeading())
    }

    @Test
    fun ready_showsRequirementsInGivenOrder() {
        show(ready)

        // Every row is laid out, on screen or not, so their positions give their order.
        val tops = listOf("Plan a campout.", "Do two of these.", "Keep a camping log.").map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    private val fortyPercentBar = hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))

    @Test
    fun inProgress_showsHowMuchIsDone_underTheName() {
        show(ready.copy(fractionDone = 0.4f))

        val bar = composeTestRule.onNode(fortyPercentBar).assertIsDisplayed().getBoundsInRoot()
        val nameBottom = composeTestRule.onNodeWithText("Camping").getBoundsInRoot().bottom
        val eagleTop = composeTestRule.onNodeWithText("Eagle-required").getBoundsInRoot().top
        // Material makes the bar's bounds taller than the bar, for touch, so its middle says
        // where it is.
        assertTrue((bar.top + bar.bottom) / 2 in nameBottom..eagleTop)
    }

    // The status card says it's in progress, so screen readers don't hear it twice.
    @Test
    fun inProgress_barReadsHowMuchIsDone_andTheStatusCardSaysInProgress() {
        show(ready.copy(status = BadgeStatus.InProgress, fractionDone = 0.4f, canClear = true))

        composeTestRule.onNode(fortyPercentBar)
            .assert(hasStateDescription("40% done"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        composeTestRule.onNodeWithText("In progress").assertIsDisplayed()
    }

    @Test
    fun notInProgress_hasNoBar() {
        show(ready)

        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertDoesNotExist()
    }

    @Test
    fun eagleRequiredBadge_isLabeled() {
        show(ready)

        composeTestRule.onNodeWithText("Eagle-required").assertIsDisplayed()
    }

    @Test
    fun eagleGroupBadge_namesTheGroup() {
        show(ready.copy(eagle = EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming"))))

        composeTestRule.onNodeWithText("Eagle-required (one of Cycling, Hiking, and Swimming)")
            .assertIsDisplayed()
    }

    // The label only gives information, so it shouldn't be announced as something to tap.
    @Test
    fun eagleLabel_isNotAButton() {
        show(ready)

        composeTestRule.onNodeWithText("Eagle-required")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    // The icon is decorative, so screen readers read only the text.
    @Test
    fun eagleLabel_isReadOnce() {
        show(ready)

        composeTestRule.onNodeWithText("Eagle-required", useUnmergedTree = true)
            .onParent()
            .onChildren()
            .filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(0)
    }

    // The tag's fill is what sets the label apart from the blue text buttons below it. Only
    // Robolectric's native graphics draw real pixels and measure real text. On SDK 36, as the
    // screenshot tests are: on SDK 37, Robolectric 4.17 leaves a class's later captures blank.
    @Config(sdk = [36])
    @Test
    fun eagleLabel_isOnTheTagsFill() {
        composeTestRule.setContent {
            BlueCardTheme { NoActionsBadgeDetailScreen(ready) }
        }

        // The text's top-left corner is clear of its letters, so it shows what's behind them.
        val pixels = composeTestRule.onNodeWithText("Eagle-required").captureToImage().toPixelMap()
        assertEquals(BlueCardLightColorScheme.primaryFixedDim, pixels[0, 0])
    }

    // At twice the font size on a narrow phone, a group's label needs several lines.
    @Config(qualifiers = "w320dp")
    @Test
    fun longEagleLabel_atLargeFontSize_wrapsWithoutBeingCutOff() {
        val group = EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming"))
        composeTestRule.setContent {
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalDensity provides density) {
                NoActionsBadgeDetailScreen(ready.copy(eagle = group))
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText("Eagle-required", substring = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("Expected 3+ lines, was ${layout.lineCount}", layout.lineCount >= 3)
        assertFalse("Expected nothing cut off", layout.hasVisualOverflow)
    }

    @Test
    fun notEagleRequiredBadge_hasNoLabel() {
        show(ready.copy(eagle = null))

        composeTestRule.onNodeWithText("Eagle-required", substring = true).assertDoesNotExist()
    }

    @Test
    fun officialLink_opensOfficialPage() {
        show(ready)

        composeTestRule.onNodeWithText("Official requirements")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        val started = startedActivity()
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.scouting.org/merit-badges/camping/", started?.dataString)
    }

    // Screen readers say where the link goes, and don't read its icon.
    @Test
    fun officialLink_saysItOpensTheBrowser() {
        show(ready)

        composeTestRule.onNodeWithText("Official requirements")
            .assert(hasClickLabel("open in browser"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    // Labeling the click mustn't take away the click itself.
    @Test
    fun officialLink_screenReaderTap_opensOfficialPage() {
        show(ready)

        composeTestRule.onNodeWithText("Official requirements")
            .performSemanticsAction(SemanticsActions.OnClick)

        val started = startedActivity()
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.scouting.org/merit-badges/camping/", started?.dataString)
    }

    // The button merges its label and icon, so these look inside it.
    private fun officialLinkLabel() = composeTestRule
        .onNodeWithText("Official requirements", useUnmergedTree = true)
        .getBoundsInRoot()

    private fun officialLinkIcon() = composeTestRule
        .onNodeWithTag(OFFICIAL_LINK_ICON_TAG, useUnmergedTree = true)
        .getBoundsInRoot()

    @Test
    fun officialLink_iconFollowsLabel() {
        show(ready)

        assertTrue(officialLinkIcon().left >= officialLinkLabel().right)
    }

    @Test
    fun officialLink_rightToLeft_iconFollowsLabel() {
        show(ready, LayoutDirection.Rtl)

        assertTrue(officialLinkIcon().right <= officialLinkLabel().left)
    }

    // The browser takes a moment to cover BlueCard, so the second tap reaches the link too.
    @Test
    fun officialLink_doubleTap_opensBrowserOnce() {
        show(ready)

        composeTestRule.onNodeWithText("Official requirements").performTouchInput { doubleClick() }

        assertEquals(Intent.ACTION_VIEW, startedActivity()?.action)
        assertNull(startedActivity())
    }

    @Test
    fun noCounselor_offersToAddOne() {
        show(ready)

        composeTestRule.onNodeWithText("Counselor").performScrollTo().assert(isHeading())
        composeTestRule.onNodeWithText("Add counselor").performScrollTo().performClick()

        assertEquals(1, counselorEdits)
        composeTestRule.onNodeWithText("Edit counselor").assertDoesNotExist()
    }

    @Test
    fun counselor_showsNamePhoneAndEmail_andCanBeEdited() {
        show(ready.copy(counselor = counselor))

        composeTestRule.onNodeWithText("Pat Lee").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+1 555-0100").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("pat@example.com").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Edit counselor").performScrollTo().performClick()

        assertEquals(1, counselorEdits)
        composeTestRule.onNodeWithText("Add counselor").assertDoesNotExist()
    }

    @Test
    fun counselor_isBetweenOfficialLinkAndRequirements() {
        show(ready.copy(counselor = counselor))

        // Every part is laid out, on screen or not, so their positions give their order.
        val tops = listOf(
            "Official requirements",
            "Counselor",
            "Pat Lee",
            "Requirements"
        ).map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun counselorWithOnlyAName_hasNoWayToReachThem() {
        show(ready.copy(counselor = Counselor(name = "Pat Lee")))

        composeTestRule.onNodeWithText("Pat Lee").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(hasClickLabel("call")).assertDoesNotExist()
        composeTestRule.onNode(hasClickLabel("send email")).assertDoesNotExist()
    }

    @Test
    fun counselorPhone_opensPhoneAppWithTheNumber() {
        show(ready.copy(counselor = counselor))

        composeTestRule.onNodeWithText("+1 555-0100").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickLabel("call"))
            .performClick()

        val started = startedActivity()
        assertEquals(Intent.ACTION_DIAL, started?.action)
        assertEquals("tel", started?.data?.scheme)
        assertEquals("+1 555-0100", started?.data?.schemeSpecificPart)
    }

    @Test
    fun counselorPhone_doubleTap_opensPhoneAppOnce() {
        show(ready.copy(counselor = counselor))

        composeTestRule.onNodeWithText("+1 555-0100").performScrollTo()
            .performTouchInput { doubleClick() }

        assertEquals(Intent.ACTION_DIAL, startedActivity()?.action)
        assertNull(startedActivity())
    }

    @Test
    fun counselorEmail_opensEmailAppToWriteToThem() {
        show(ready.copy(counselor = counselor))

        composeTestRule.onNodeWithText("pat@example.com").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickLabel("send email"))
            .performClick()

        val started = startedActivity()
        assertEquals(Intent.ACTION_SENDTO, started?.action)
        // Spelled as typed, since some email apps show the address as the link spells it.
        assertEquals("mailto:pat@example.com", started?.dataString)
    }

    @Test
    fun counselorEmail_doubleTap_opensEmailAppOnce() {
        show(ready.copy(counselor = counselor))

        composeTestRule.onNodeWithText("pat@example.com").performScrollTo()
            .performTouchInput { doubleClick() }

        assertEquals(Intent.ACTION_SENDTO, startedActivity()?.action)
        assertNull(startedActivity())
    }

    // All three controls share one guard, so a quick tap on another control, before the first
    // app covers BlueCard, doesn't open a second app. The clock is stopped, so the taps all
    // come within the double-tap timeout.
    @Test
    fun officialLink_thenCounselorPhoneAndEmail_opensOnlyBrowser() {
        show(ready.copy(counselor = counselor))
        // Scrolled first, since a scroll can't finish while the clock is paused.
        composeTestRule.onNodeWithText("pat@example.com").performScrollTo()
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText("Official requirements").performClick()
        composeTestRule.onNodeWithText("+1 555-0100").performScrollTo().performClick()
        composeTestRule.onNodeWithText("pat@example.com").performScrollTo().performClick()

        assertEquals(Intent.ACTION_VIEW, startedActivity()?.action)
        assertNull(startedActivity())
    }

    @Test
    fun counselorEmail_withCharactersThatWouldChangeTheLink_areEncoded() {
        // A ? would start the email's headers, such as its subject.
        show(ready.copy(counselor = Counselor(email = "pat+scouts?x@example.com")))

        composeTestRule.onNodeWithText("pat+scouts?x@example.com").performScrollTo().performClick()

        val started = startedActivity()
        assertEquals("mailto:pat+scouts%3Fx@example.com", started?.dataString)
        assertEquals("pat+scouts?x@example.com", started?.data?.schemeSpecificPart)
    }

    @Test
    fun counselorPhone_withNoPhoneApp_showsMessage() {
        show(ready.copy(counselor = counselor))
        // Starting an activity nothing can handle now fails, as on a tablet without a phone app.
        shadowOf(application).checkActivities(true)

        composeTestRule.onNodeWithText("+1 555-0100").performScrollTo().performClick()

        assertEquals(
            "No app on this phone can call the number.",
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun counselorEmail_withNoEmailApp_showsMessage() {
        show(ready.copy(counselor = counselor))
        shadowOf(application).checkActivities(true)

        composeTestRule.onNodeWithText("pat@example.com").performScrollTo().performClick()

        assertEquals("No app on this phone can send email.", ShadowToast.getTextOfLatestToast())
    }

    // The page is laid out left-to-right, like the English strings, but a name typed in Persian
    // keeps its own direction: its final period is drawn at its end, which is on its left.
    @Test
    fun counselorNameTypedInPersian_keepsItsPunctuationAtItsEnd() {
        show(ready.copy(counselor = Counselor(name = "علی رضایی.")))

        val name = composeTestRule.onNodeWithText("علی رضایی.", substring = true).visualText()

        assertTrue(name, name.startsWith("."))
    }

    @Test
    fun requirement_saysWhetherItsComplete() {
        show(ready)

        row("Plan a campout.").assert(hasStateDescription("Completed"))
        row("Keep a camping log.").assert(hasStateDescription("Not completed"))
        row("Do all of these.").assert(hasStateDescription("Completed"))
        row("Do two of these.").assert(hasStateDescription("Not completed"))
    }

    // The check on its number is drawn only: screen readers read "Completed" once, as its state.
    @Test
    fun completeRequirement_saysCompletedOnce() {
        show(ready)

        row("Plan a campout.")
            .assert(hasStateDescription("Completed"))
            .assert(!hasContentDescription("Completed"))
            .assert(!hasText("Completed"))
    }

    // The scout marks a requirement complete on its own page.
    @Test
    fun requirements_haveNoCheckbox() {
        show(ready)

        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun choiceRequirement_showsHowManyAreNeeded() {
        show(ready)

        row("Do two of these.").assert(hasText("Do 2 of 3"))
        row("Plan a campout.").assert(!hasText("Do", substring = true))
    }

    private fun withPartlyCompleted(number: String, count: CompleteCount?) = ready.copy(
        requirements = ready.requirements.map {
            if (it.number == number) {
                it.copy(completed = false, partlyCompleted = true, completeCount = count)
            } else {
                it
            }
        }
    )

    @Test
    fun partlyCompletedChoice_saysInProgress_andHowManyOfThoseNeededAreComplete() {
        show(withPartlyCompleted("2", CompleteCount(1, 2)))

        row("Do two of these.")
            .assert(hasStateDescription("In progress"))
            .assert(hasText("Do 2 of 3 (1 of 2 complete)"))
    }

    @Test
    fun partlyCompletedRequirementNeedingAll_saysHowManyAreComplete() {
        show(withPartlyCompleted("4", CompleteCount(2, 3)))

        row("Do all of these.")
            .assert(hasStateDescription("In progress"))
            .assert(hasText("(2 of 3 complete)"))
    }

    @Test
    fun partlyCompletedWithOnlyItsOwnWorkLeft_saysWhatsStillToDo() {
        show(
            ready.copy(
                requirements = ready.requirements.map {
                    if (it.number == "2") {
                        it.copy(
                            completed = false,
                            partlyCompleted = true,
                            completeCount = CompleteCount(2, 2),
                            ownWork = OwnWork(
                                "Share what you learned with your counselor.",
                                completed = false
                            ),
                            stillToDo = "Share what you learned with your counselor."
                        )
                    } else {
                        it
                    }
                }
            )
        )

        row("Do two of these.")
            .assert(hasStateDescription("In progress"))
            .assert(hasText("Do 2 of 3 (2 of 2 complete)"))
            .assert(hasText("Still to do: Share what you learned with your counselor."))
    }

    // Only its own work, or a requirement further down, is complete.
    @Test
    fun partlyCompletedWithNoSubRequirementComplete_hasNoCount() {
        show(withPartlyCompleted("2", null))

        row("Do two of these.")
            .assert(hasStateDescription("In progress"))
            .assert(hasText("Do 2 of 3"))
            .assert(!hasText("complete", substring = true))
    }

    // Under the sub-requirements' count it's what's left of, not the tracker's.
    @Test
    fun subRequirementsAndTrackerWithOnlyItsOwnWorkLeft_saysWhatsStillToDoUnderTheirCount() {
        show(
            ready.copy(
                requirements = ready.requirements.map {
                    if (it.number == "2") {
                        it.copy(
                            completed = false,
                            partlyCompleted = true,
                            completeCount = CompleteCount(2, 2),
                            tracker = TrackerCount(2, null, "sessions"),
                            ownWork = OwnWork("Share what you learned.", completed = false),
                            stillToDo = "Share what you learned."
                        )
                    } else {
                        it
                    }
                }
            )
        )

        val texts = row("Do two of these.")
            .fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(
            listOf(
                "2",
                "Do two of these.",
                "Do 2 of 3 (2 of 2 complete)",
                "Still to do: Share what you learned.",
                "2 sessions"
            ),
            texts
        )
    }

    // Under the rows' count, as under a count of sub-requirements.
    @Test
    fun everyRowFilledInWithOnlyItsOwnWorkLeft_saysWhatsStillToDo() {
        show(
            ready.copy(
                requirements = ready.requirements.map {
                    if (it.number == "3") {
                        it.copy(
                            partlyCompleted = true,
                            tracker = TrackerCount(12, 12, "nights"),
                            ownWork = OwnWork("Compare the nights.", completed = false),
                            stillToDo = "Compare the nights."
                        )
                    } else {
                        it
                    }
                }
            )
        )

        val texts = row("Keep a camping log.")
            .assert(hasStateDescription("In progress"))
            .fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(
            listOf(
                "3",
                "Keep a camping log.",
                "12 of 12 nights",
                "Still to do: Compare the nights."
            ),
            texts
        )
    }

    @Test
    fun requirementWithTracker_showsHowMuchIsFilledIn() {
        show(ready)

        row("Keep a camping log.").assert(hasText("8 of 12 nights"))
    }

    @Test
    fun everyRequirement_isButtonThatOpensIt() {
        show(ready)

        for (summary in listOf("Plan a campout.", "Do two of these.", "Keep a camping log.")) {
            row(summary)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(
                    SemanticsMatcher("click label is \"open requirement\"") {
                        it.config[SemanticsActions.OnClick].label == "open requirement"
                    }
                )
                .performClick()
        }

        assertEquals(listOf("1", "2", "3"), openedRequirements)
    }

    // At twice the font size, "10" outgrows the box's minimum width, while "9" doesn't.
    @Config(fontScale = 2f)
    @Test
    fun requirementNumbersOfDifferentWidths_atLargestFontSize_summariesLineUp() {
        val requirements = (9..10).map {
            RequirementItem("$it", "Requirement $it.", null, false, markedByHand = false)
        }
        show(ready.copy(requirements = requirements))

        // In the unmerged tree, each summary is a node of its own.
        val starts = requirements.map {
            composeTestRule.onNodeWithText(it.summary, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .left
        }
        assertEquals(starts.first(), starts.last())
    }

    @Test
    fun unavailable_showsMessageOnly() {
        show(BadgeDetailUiState.Unavailable)

        composeTestRule
            .onNodeWithText("This badge's requirements aren't in this version of BlueCard.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirements").assertDoesNotExist()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
    }

    @Test
    fun unavailable_isAnnouncedWhenItReplacesLoading() {
        show(BadgeDetailUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "This badge's requirements aren't in this version of BlueCard."
        ) { uiState = BadgeDetailUiState.Unavailable }
    }

    @Test
    fun loadFailed_showsMessageOnly() {
        show(BadgeDetailUiState.LoadFailed)

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirements").assertDoesNotExist()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
    }

    @Test
    fun loadFailed_isAnnouncedWhenItReplacesLoading() {
        show(BadgeDetailUiState.Loading)

        composeTestRule.assertAnnouncedWhenShown(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ) { uiState = BadgeDetailUiState.LoadFailed }
    }

    @Test
    fun longRequirementList_scrollsToLastRequirement() {
        // More top-level requirements than any badge has.
        val many = (1..20).map {
            RequirementItem("$it", "Requirement $it.", null, false, markedByHand = false)
        }
        show(ready.copy(requirements = many))

        row("Requirement 20.").assertIsDisplayed().performClick()

        assertEquals(listOf("20"), openedRequirements)
    }

    private val completed = ready.copy(status = BadgeStatus.Completed)

    private val marked = completed.copy(
        completedOnPriorDate = LocalDate.of(2025, 8, 1),
        completedOn = LocalDate.of(2025, 8, 1)
    )

    @Test
    fun incompleteBadge_canBeMarkedCompleted_onADayUpToToday() {
        show(ready)

        composeTestRule.onNodeWithText("Mark completed").performScrollTo().performClick()
        // It opens at today.
        pickerDay("May 20, 2026").assert(isSelected)
        pickerDay("May 21, 2026").assertIsNotEnabled()
        pickerDay("May 10, 2026").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf(LocalDate.of(2026, 5, 10)), marks)
        composeTestRule.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun badgeNotStarted_saysSo_andAsksWhetherItsAlreadyCompleted_aboveMarkCompleted() {
        show(ready)

        val tops = listOf("Not started", "Already completed this badge?", "Mark completed")
            .map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    // Once it's started, as on Badges.
    @Test
    fun badgeInProgress_saysSo() {
        show(ready.copy(status = BadgeStatus.InProgress, fractionDone = 0.4f, canClear = true))

        composeTestRule.onNodeWithText("In progress").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Not started").assertDoesNotExist()
    }

    @Test
    fun badgeCompleteFromItsRequirements_saysWhen_aboveItsReport() {
        show(completed.copy(completedOn = LocalDate.of(2026, 4, 15)))

        composeTestRule.onNodeWithText("Completed on Apr 15, 2026").performScrollTo()
            .assertIsDisplayed()
        val tops = listOf(
            "Official requirements",
            "Completed on Apr 15, 2026",
            "Share report",
            "Counselor"
        ).map(::topOf)
        assertEquals(tops.sorted(), tops)
        composeTestRule.onNodeWithText("Already completed this badge?").assertDoesNotExist()
        composeTestRule.onNodeWithText("Mark completed").assertDoesNotExist()
        composeTestRule.onNodeWithText("Change date").assertDoesNotExist()
    }

    @Test
    fun badgeCompleteFromItsRequirements_withoutADate_saysCompleted() {
        show(completed)

        composeTestRule.onNodeWithText("Completed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun markCompleted_isBetweenOfficialLinkAndCounselor() {
        show(ready)

        val tops = listOf("Official requirements", "Mark completed", "Counselor").map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    // So a mistaken Unmark loses nothing.
    @Test
    fun markCompleted_afterUnmarking_opensAtTheDateUnmarked() {
        show(ready.copy(unmarkedDate = LocalDate.of(2026, 4, 15)))

        composeTestRule.onNodeWithText("Mark completed").performScrollTo().performClick()
        pickerDay("April 15, 2026").assert(isSelected)
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf(LocalDate.of(2026, 4, 15)), marks)
    }

    @Test
    fun markCompleted_cancelled_marksNothing() {
        show(ready)

        composeTestRule.onNodeWithText("Mark completed").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<LocalDate>(), marks)
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun markCompleted_onAPageOpenPastMidnight_offersTheNewDay() {
        show(ready)
        today = LocalDate.of(2026, 5, 21)

        composeTestRule.onNodeWithText("Mark completed").performScrollTo().performClick()
        pickerDay("May 22, 2026").assertIsNotEnabled()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf(LocalDate.of(2026, 5, 21)), marks)
    }

    @Test
    fun markedBadge_showsItsDate_aboveItsReport() {
        show(marked)

        composeTestRule.onNodeWithText("Mark completed").assertDoesNotExist()
        composeTestRule.onNodeWithText("Already completed this badge?").assertDoesNotExist()
        val tops = listOf(
            "Official requirements",
            "Completed on Aug 1, 2025",
            "Change date",
            "Share report",
            "Counselor"
        ).map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun markedBadge_changeDate_opensAtItsDate_andMarksTheDayPicked() {
        show(marked)

        composeTestRule.onNodeWithText("Change date").performScrollTo().performClick()
        pickerDay("August 1, 2025").assert(isSelected)
        pickerDay("August 5, 2025").performClick()
        composeTestRule.onNodeWithText("OK").performClick()

        assertEquals(listOf(LocalDate.of(2025, 8, 5)), marks)
    }

    @Test
    fun markedBadge_unmark_unmarksIt() {
        show(marked)

        composeTestRule.onNodeWithText("Unmark").performScrollTo().performClick()

        assertEquals(1, unmarks)
        assertEquals(emptyList<LocalDate>(), marks)
    }

    @Test
    fun badgeCompleteFromItsRequirements_cantBeMarked() {
        show(completed)

        composeTestRule.onNodeWithText("Mark completed").assertDoesNotExist()
        composeTestRule.onNodeWithText("Unmark").assertDoesNotExist()
    }

    @Test
    fun requirementNotRecorded_onAMarkedBadge_saysSo() {
        val notRecorded =
            RequirementItem("1", "Plan a campout.", null, false, true, notRecorded = true)
        show(marked.copy(requirements = listOf(notRecorded)))

        row("Plan a campout.").assert(hasStateDescription("Not recorded"))
    }

    @Test
    fun incompleteBadge_hasNoReport() {
        show(ready)

        composeTestRule.onNodeWithText("Share report").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save report").assertDoesNotExist()
    }

    @Test
    fun completeBadge_offersToShareAndSaveItsReport() {
        show(completed)

        listOf("Share report", "Save report").forEach {
            composeTestRule.onNodeWithText(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertIsDisplayed()
        }
    }

    @Test
    fun reportButtons_areBetweenOfficialLinkAndCounselor() {
        show(completed)

        val tops = listOf("Official requirements", "Share report", "Counselor").map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun shareReport_asksForTheReport() {
        show(completed)

        composeTestRule.onNodeWithText("Share report").performClick()

        assertEquals(1, reportShareRequests)
        // The share sheet opens once the report is ready.
        assertNull(startedActivity())
    }

    // The share sheet opens once the report is created, so the second tap of a double tap
    // could come after it's ready and create it again.
    @Test
    fun shareReport_doubleTap_asksForTheReportOnce() {
        show(completed)

        composeTestRule.onNodeWithText("Share report").performTouchInput { doubleClick() }

        assertEquals(1, reportShareRequests)
    }

    @Test
    fun reportToShare_opensShareSheetWithIt_once() {
        val report = Uri.parse("content://reports/camping.pdf")
        show(completed.copy(reportToShare = report))
        composeTestRule.waitForIdle()

        val chooser = startedActivity()
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val send =
            IntentCompat.getParcelableExtra(chooser!!, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertEquals(
            report,
            IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)
        )
        // Lets the app the scout picks read the report, and the share sheet show it.
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(report, send.clipData?.getItemAt(0)?.uri)
        assertEquals(1, reportsShared)
        assertNull(startedActivity())
    }

    @Test
    fun saveReport_asksWhereToSaveIt_andSavesItThere() {
        show(completed)

        composeTestRule.onNodeWithText("Save report").performClick()

        val picker = launchedForResult.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        assertEquals("application/pdf", picker.type)
        assertEquals("Camping merit badge report.pdf", picker.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(listOf(destination), reportsSaved)
    }

    @Test
    fun leavingFilePickerWithoutSaving_savesNothing() {
        pickDestination = { null }
        show(completed)

        composeTestRule.onNodeWithText("Save report").performClick()

        assertEquals(1, launchedForResult.size)
        assertEquals(emptyList<Uri>(), reportsSaved)
    }

    // The file picker takes a moment to cover BlueCard, so the second tap reaches the button.
    @Test
    fun saveReport_doubleTap_opensFilePickerOnce() {
        show(completed)

        composeTestRule.onNodeWithText("Save report").performTouchInput { doubleClick() }

        assertEquals(1, launchedForResult.size)
    }

    @Test
    fun saveReport_withNoFilePicker_showsMessage() {
        pickDestination = { throw ActivityNotFoundException() }
        show(completed)

        composeTestRule.onNodeWithText("Save report").performClick()

        assertEquals("No app on this phone can save files.", ShadowToast.getTextOfLatestToast())
        assertEquals(emptyList<Uri>(), reportsSaved)
    }

    @Test
    fun reportFailed_showsMessage_thenReportsItShown() {
        val failure = TaskFailure()
        show(completed.copy(reportFailure = failure))

        val message = "Couldn't create the report. Try again."
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), reportFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(message).assertDoesNotExist()
        assertEquals(listOf(failure), reportFailuresShown)
    }

    private val startedBadge = ready.copy(canClear = true)

    private fun clearButton() = composeTestRule.onNodeWithText("Clear progress").performScrollTo()

    private fun confirmClear() =
        composeTestRule.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()

    private val clearTitle = "Clear progress on Camping?"

    @Test
    fun badgeNotStarted_hasNoClearButton() {
        show(ready)

        composeTestRule.onNodeWithText("Clear progress").assertDoesNotExist()
    }

    @Test
    fun clearButton_isLastOnThePage() {
        show(startedBadge.copy(counselor = counselor))

        val tops = listOf("Counselor", "Do all of these.", "Clear progress").map(::topOf)
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun clear_asksFirst_thenClears() {
        show(startedBadge)

        clearButton().performClick()
        composeTestRule.onNodeWithText(clearTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText("What you recorded for it will be removed.")
            .assertIsDisplayed()
        assertEquals(0, clears)
        confirmClear()

        assertEquals(1, clears)
        composeTestRule.onNodeWithText(clearTitle).assertDoesNotExist()
    }

    @Test
    fun clear_withCounselor_saysTheyAreRemovedToo() {
        show(startedBadge.copy(counselor = counselor))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText("What you recorded for it will be removed, including its counselor.")
            .assertIsDisplayed()
    }

    @Test
    fun clear_ofABadgeRanksCountOn_namesTheRanksThatWontCountAsEarned() {
        show(startedBadge.copy(counselor = counselor, unearnedByClear = listOf("Star", "Life")))

        clearButton().performClick()

        composeTestRule
            .onNodeWithText(
                "What you recorded for it will be removed, including its counselor. Star and " +
                    "Life will no longer count as earned."
            )
            .assertIsDisplayed()
    }

    @Test
    fun clear_cancel_clearsNothing() {
        show(startedBadge)

        clearButton().performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(0, clears)
        composeTestRule.onNodeWithText(clearTitle).assertDoesNotExist()
    }

    @Test
    fun clear_back_closesTheDialog_andClearsNothing() {
        show(startedBadge)

        clearButton().performClick()
        // Espresso's pressBack doesn't reach the dialog's window under Robolectric, so Back is
        // sent to the dialog itself.
        composeTestRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
                .onBackPressed()
        }

        assertEquals(0, clears)
        composeTestRule.onNodeWithText(clearTitle).assertDoesNotExist()
        composeTestRule.onNodeWithText("Clear progress").assertExists()
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        val failure = TaskFailure()
        show(startedBadge.copy(saveFailure = failure))

        val message = "Couldn't save. Try again."
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        assertEquals(emptyList<TaskFailure>(), saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(message).assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
        assertEquals(emptyList<TaskFailure>(), reportFailuresShown)
    }

    // Material shows one snackbar at a time.
    @Test
    fun saveAndReportFailures_atOnce_showOneAfterTheOther() {
        val reportFailure = TaskFailure()
        val saveFailure = TaskFailure()
        show(
            completed.copy(
                canClear = true,
                reportFailure = reportFailure,
                saveFailure = saveFailure
            )
        )

        val report = "Couldn't create the report. Try again."
        val save = "Couldn't save. Try again."
        composeTestRule.onNodeWithText(report).assertIsDisplayed()
        composeTestRule.onNodeWithText(save).assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(report).assertDoesNotExist()
        composeTestRule.onNodeWithText(save).assertIsDisplayed()
        assertEquals(listOf(reportFailure), reportFailuresShown)

        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText(save).assertDoesNotExist()
        assertEquals(listOf(saveFailure), saveFailuresShown)
    }
}
