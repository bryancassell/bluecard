package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * How a requirement's row looks in each state, which screen readers can't tell apart from
 * its semantics: the number's box outlined, tinted, filled with a check, or filled in the highest
 * surface (grey in light mode, Dark Blue in dark mode). Each test checks its rows against a
 * reference image in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A fixed screen, so every machine draws the same image. On SDK 37, Robolectric 4.17 draws only
// the first screenshot in a class and leaves the rest blank; SDK 36 draws them all.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class RequirementRowScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(vararg items: RequirementItem) {
        composeTestRule.setContent {
            BlueCardTheme { RequirementRows(items = items.toList(), onOpen = {}) }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private val notCompletedItem = RequirementItem(
        "3",
        "Plan an overnight trek and find your way with a topo map.",
        Choice(1, 3),
        completed = false,
        markedByHand = false
    )

    private val completedItem = RequirementItem(
        "2",
        "Learn Leave No Trace and the Outdoor Code, and plan to follow them.",
        null,
        completed = true,
        markedByHand = true
    )

    private val partlyCompletedItem = RequirementItem(
        "3",
        "Plan an overnight trek and find your way with a topo map.",
        Choice(2, 3),
        completed = false,
        markedByHand = false,
        partlyCompleted = true,
        completeCount = CompleteCount(1, 2)
    )

    private val notNeededItem = RequirementItem(
        "3b",
        "Use a GPS receiver.",
        null,
        completed = false,
        markedByHand = true,
        notNeeded = true
    )

    @Test
    fun notCompleted() = capture(notCompletedItem)

    @Test
    fun completed() = capture(completedItem)

    @Test
    fun partlyCompleted() = capture(partlyCompletedItem)

    @Test
    fun notNeeded() = capture(notNeededItem)

    // Every state in dark mode, where the boxes are navy, Pale Blue and Dark Blue.
    @Test
    @Config(qualifiers = "+night")
    fun everyState_darkMode() =
        capture(notCompletedItem, partlyCompletedItem, completedItem, notNeededItem)

    // The box widens to fit the number.
    @Test
    fun completed_longNumber() = capture(
        RequirementItem("8a(1)", "Propane or butane stoves.", null, true, markedByHand = true)
    )

    // The box grows on every side, keeping the number clear of its edges and of the check.
    @Test
    @Config(fontScale = 2f)
    fun completed_largestFont() = capture(
        RequirementItem(
            "10",
            "Discuss what camping taught you and how Scout ideals apply.",
            null,
            completed = true,
            markedByHand = true
        )
    )
}
