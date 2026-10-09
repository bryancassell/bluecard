package io.github.bryancassell.bluecard.ui.badges

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.progress.BadgeStatus
import io.github.bryancassell.bluecard.testing.NARROW_SCREEN
import io.github.bryancassell.bluecard.testing.assertNoWordBroken
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where a row puts the scout's status: at its end, unless that would break a word of the text
 * beside it (#307). What the rows say is tested on the pages that list them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvancementRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val swimming = BadgeListItem(
        id = "swimming",
        name = "Swimming",
        eagle = EagleRequirement.Required,
        status = BadgeStatus.InProgress,
        fractionDone = 0.4f
    )

    private fun showBadge(badge: BadgeListItem) {
        composeTestRule.setContent {
            BlueCardTheme {
                BadgeRow(badge, rememberBadgeNameListFormatter(), onClick = {})
            }
        }
    }

    /** Where [text] is drawn, found in the unmerged tree, where it's a node of its own. */
    private fun boundsOf(text: String): DpRect =
        composeTestRule.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()

    private fun assertNoWordBroken(vararg texts: String) = texts.forEach {
        composeTestRule.onNodeWithText(it, useUnmergedTree = true).assertNoWordBroken()
    }

    private fun lineCount(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().lineCount
    }

    /** The row's texts in the order screen readers read them. */
    private fun rowTexts() = composeTestRule.onNode(hasClickAction()).fetchSemanticsNode()
        .config[SemanticsProperties.Text].map { it.text }

    // At the largest text and display size, "In progress" left too little room beside it for
    // "Swimming" and "Eagle-required".
    @Config(qualifiers = NARROW_SCREEN, fontScale = 2f)
    @Test
    fun atTheLargestSizes_aStatusThatWouldBreakAWord_goesUnderTheName_aboveTheBar() {
        showBadge(swimming)

        assertNoWordBroken("Swimming", "Eagle-required", "In progress")
        val name = boundsOf("Swimming")
        val detail = boundsOf("Eagle-required")
        val status = boundsOf("In progress")
        val bar = composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f)), true)
            .getBoundsInRoot()
        assertTrue(status.top >= detail.bottom)
        // Material extends the bar's bounds above and below it, for touch exploration.
        assertTrue((bar.top + bar.bottom) / 2 > status.bottom)
        assertEquals(name.left, status.left)
        // Read in the same order as when it's beside them.
        assertEquals(listOf("Swimming", "Eagle-required", "In progress"), rowTexts())
    }

    // As in Star 3's list of badges.
    @Config(qualifiers = NARROW_SCREEN, fontScale = 2f)
    @Test
    fun atTheLargestSizes_aStatusThatWouldBreakAWord_goesUnderARowWithoutABar() {
        composeTestRule.setContent {
            BlueCardTheme {
                AdvancementRow(
                    name = "Swimming",
                    detail = "Completed on May 18, 2026",
                    status = "Eagle-required",
                    fractionDone = null,
                    onClickLabel = "open badge",
                    onClick = {}
                )
            }
        }

        assertNoWordBroken("Swimming", "Completed on May 18, 2026", "Eagle-required")
        val name = boundsOf("Swimming")
        val status = boundsOf("Eagle-required")
        assertTrue(status.top >= boundsOf("Completed on May 18, 2026").bottom)
        assertEquals(name.left, status.left)
    }

    // A long name wraps between its words beside the status, as at the default sizes.
    @Config(qualifiers = NARROW_SCREEN)
    @Test
    fun aStatusThatLeavesRoomForEveryWord_staysAtTheRowsEnd_besideAWrappedName() {
        showBadge(swimming.copy(name = "Citizenship in the Community", eagle = null))

        assertNoWordBroken("Citizenship in the Community")
        val name = boundsOf("Citizenship in the Community")
        val status = boundsOf("In progress")
        assertTrue(status.left >= name.right)
        assertTrue(status.top < name.bottom)
        assertEquals(2, lineCount("Citizenship in the Community"))
    }
}
