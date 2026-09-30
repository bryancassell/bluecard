package io.github.bryancassell.bluecard.ui.badge

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.Counselor
import io.github.bryancassell.bluecard.testing.visualText
import io.github.bryancassell.bluecard.ui.SaveFailure
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class BadgeDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedRequirements = mutableListOf<String>()
    private val openedUris = mutableListOf<String>()
    private val completedChanges = mutableListOf<Pair<String, Boolean>>()
    private var counselorEdits = 0
    private val saveFailuresShown = mutableListOf<SaveFailure>()

    private val ready = BadgeDetailUiState.Ready(
        name = "Camping",
        summary = "Our summary of Camping.",
        eagle = EagleRequirement.Required,
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirements = listOf(
            RequirementItem("1", "Plan a campout.", null, true, hasSubRequirements = false),
            RequirementItem(
                "2",
                "Do two of these.",
                Choice(2, 3),
                false,
                hasSubRequirements = true
            ),
            RequirementItem("3", "Keep a camping log.", null, false, hasSubRequirements = false),
            RequirementItem("4", "Do all of these.", null, true, hasSubRequirements = true)
        )
    )

    private fun show(uiState: BadgeDetailUiState) {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUris += uri
            }
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                BadgeDetailScreen(
                    uiState = uiState,
                    onOpenRequirement = { openedRequirements += it },
                    onEditCounselor = { counselorEdits++ },
                    onCompletedChange = { number, completed ->
                        completedChanges +=
                            number to completed
                    },
                    onSaveFailureShown = { saveFailuresShown += it }
                )
            }
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

    private fun checkbox(number: String) = composeTestRule
        .onNode(hasContentDescription("Requirement $number completed") and isToggleable())
        .performScrollTo()

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

        assertEquals(listOf("https://www.scouting.org/merit-badges/camping/"), openedUris)
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
    fun requirementWithoutSubRequirements_hasCheckboxShowingCompletion() {
        show(ready)

        checkbox("1").assertIsOn()
        checkbox("3").assertIsOff()
    }

    @Test
    fun requirementWithSubRequirements_hasCheckOnlyWhenComplete_andNoCheckbox() {
        show(ready)

        row("Do all of these.").assert(hasContentDescription("Completed"))
        row("Do two of these.").assert(!hasContentDescription("Completed"))
        composeTestRule.onNode(
            hasContentDescription("Requirement 2 completed")
        ).assertDoesNotExist()
        composeTestRule.onNode(
            hasContentDescription("Requirement 4 completed")
        ).assertDoesNotExist()
    }

    @Test
    fun checkingRequirement_marksItCompleted() {
        show(ready)

        checkbox("3").performClick()

        assertEquals(listOf("3" to true), completedChanges)
        assertEquals(emptyList<String>(), openedRequirements)
    }

    @Test
    fun uncheckingRequirement_marksItNotCompleted() {
        show(ready)

        checkbox("1").performClick()

        assertEquals(listOf("1" to false), completedChanges)
    }

    @Test
    fun choiceRequirement_showsHowManyAreNeeded() {
        show(ready)

        row("Do two of these.").assert(hasText("Do 2 of 3"))
        row("Plan a campout.").assert(!hasText("Do", substring = true))
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
        assertEquals(emptyList<Pair<String, Boolean>>(), completedChanges)
    }

    @Test
    fun saveFailed_showsMessage_thenReportsItShown() {
        val failure = SaveFailure()
        show(ready.copy(saveFailure = failure))

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        assertEquals(emptyList<SaveFailure>(), saveFailuresShown)

        // A short snackbar shows for 4 seconds.
        composeTestRule.mainClock.advanceTimeBy(5_000)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
        assertEquals(listOf(failure), saveFailuresShown)
    }

    @Test
    fun noSaveFailure_showsNoMessage() {
        show(ready)

        composeTestRule.onNodeWithText("Couldn't save. Try again.").assertDoesNotExist()
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
        val many = (1..20).map { RequirementItem("$it", "Requirement $it.", null, false, true) }
        show(ready.copy(requirements = many))

        row("Requirement 20.").assertIsDisplayed().performClick()

        assertEquals(listOf("20"), openedRequirements)
    }
}
