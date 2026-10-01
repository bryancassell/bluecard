package io.github.bryancassell.bluecard.ui.badge

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.testing.visualText
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import io.github.bryancassell.bluecard.ui.theme.BlueCardColorScheme
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
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
import org.robolectric.shadows.ShadowToast

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class BadgeDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedRequirements = mutableListOf<String>()
    private var counselorEdits = 0

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

    private fun show(
        uiState: BadgeDetailUiState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                BadgeDetailScreen(
                    uiState = uiState,
                    onOpenRequirement = { openedRequirements += it },
                    onEditCounselor = { counselorEdits++ }
                )
            }
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

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
        val tops = listOf("Plan a campout.", "Do two of these.", "Keep a camping log.").map {
            composeTestRule.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(tops.sorted(), tops)
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
    // Robolectric's native graphics draw real pixels and measure real text.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun eagleLabel_isOnTheTagsFill() {
        composeTestRule.setContent {
            BlueCardTheme { BadgeDetailScreen(ready, onOpenRequirement = {}, onEditCounselor = {}) }
        }

        // The text's top-left corner is clear of its letters, so it shows what's behind them.
        val pixels = composeTestRule.onNodeWithText("Eagle-required").captureToImage().toPixelMap()
        assertEquals(BlueCardColorScheme.primaryFixedDim, pixels[0, 0])
    }

    // At twice the font size on a narrow phone, a group's label needs several lines.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w320dp")
    @Test
    fun longEagleLabel_atLargeFontSize_wrapsWithoutBeingCutOff() {
        val group = EagleRequirement.OneOf(listOf("Cycling", "Hiking", "Swimming"))
        composeTestRule.setContent {
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalDensity provides density) {
                BadgeDetailScreen(
                    ready.copy(eagle = group),
                    onOpenRequirement = {},
                    onEditCounselor = {}
                )
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
        val tops = listOf("Official requirements", "Counselor", "Pat Lee", "Requirements").map {
            composeTestRule.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y
        }
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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    fun loadFailed_showsMessageOnly() {
        show(BadgeDetailUiState.LoadFailed)

        composeTestRule.onNodeWithText(
            "Couldn't load your data. Try closing and reopening BlueCard."
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Requirements").assertDoesNotExist()
        composeTestRule.onNode(loadingIndicator).assertDoesNotExist()
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
}
