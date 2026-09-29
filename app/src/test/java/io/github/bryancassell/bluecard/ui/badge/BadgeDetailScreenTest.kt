package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.ui.badges.EagleRequirement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One test per UI state and interaction, with fixed UI state. */
@RunWith(AndroidJUnit4::class)
class BadgeDetailScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val openedRequirements = mutableListOf<String>()
    private val openedUris = mutableListOf<String>()

    private val ready = BadgeDetailUiState.Ready(
        name = "Camping",
        summary = "Our summary of Camping.",
        eagle = EagleRequirement.Required,
        officialUrl = "https://www.scouting.org/merit-badges/camping/",
        requirements = listOf(
            RequirementItem("1", "Plan a campout.", null, completed = true, opensDetail = false),
            RequirementItem(
                "2",
                "Do two of these.",
                Choice(2, 3),
                completed = false,
                opensDetail = true
            ),
            RequirementItem(
                "3",
                "Keep a camping log.",
                null,
                completed = false,
                opensDetail = false
            )
        )
    )

    /** [lifecycleState] is the screen's; a screen still animating in is STARTED. */
    private fun show(
        uiState: BadgeDetailUiState,
        lifecycleState: Lifecycle.State = Lifecycle.State.RESUMED
    ) {
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUris += uri
            }
        }
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle =
                LifecycleRegistry.createUnsafe(this).apply { currentState = lifecycleState }
        }
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides uriHandler,
                LocalLifecycleOwner provides lifecycleOwner
            ) {
                BadgeDetailScreen(
                    uiState = uiState,
                    onOpenRequirement = { openedRequirements += it }
                )
            }
        }
    }

    // Each row merges its texts, so a row is the node with the requirement's summary. It's
    // scrolled to first, as the page can be taller than the screen.
    private fun row(summary: String) = composeTestRule.onNodeWithText(summary).performScrollTo()

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
    fun officialLink_whileScreenIsNotResumed_doesNothing() {
        show(ready, lifecycleState = Lifecycle.State.STARTED)

        composeTestRule.onNodeWithText("Official requirements").performClick()

        assertEquals(emptyList<String>(), openedUris)
    }

    @Test
    fun completedRequirement_isChecked() {
        show(ready)

        row("Plan a campout.").assert(hasContentDescription("Completed"))
        row("Do two of these.").assert(!hasContentDescription("Completed"))
    }

    @Test
    fun choiceRequirement_showsHowManyAreNeeded() {
        show(ready)

        row("Do two of these.").assert(hasText("Do 2 of 3"))
        row("Plan a campout.").assert(!hasText("Do", substring = true))
    }

    @Test
    fun requirementWithMore_isButtonThatOpensIt() {
        show(ready)

        row("Do two of these.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("click label is \"open requirement\"") {
                    it.config[SemanticsActions.OnClick].label == "open requirement"
                }
            )
            .performClick()

        assertEquals(listOf("2"), openedRequirements)
    }

    @Test
    fun requirementWithoutMore_doesNotOpen() {
        show(ready)

        row("Plan a campout.").assert(!hasClickAction())
        row("Keep a camping log.").assert(!hasClickAction())
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
    fun longRequirementList_scrollsToLastRequirement() {
        // More top-level requirements than any badge has.
        val many = (1..20).map { RequirementItem("$it", "Requirement $it.", null, false, true) }
        show(ready.copy(requirements = many))

        row("Requirement 20.").assertIsDisplayed().performClick()

        assertEquals(listOf("20"), openedRequirements)
    }
}
