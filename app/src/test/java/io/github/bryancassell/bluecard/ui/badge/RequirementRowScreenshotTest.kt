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
 * its semantics: the number's box outlined, filled with a check, or filled in grey. Each test
 * checks its row against a reference image in `src/test/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A fixed screen, so every machine draws the same image. On SDK 37, Robolectric 4.17 draws only
// the first screenshot in a class and leaves the rest blank; SDK 36 draws them all.
@Config(qualifiers = "w360dp-h640dp-xhdpi", sdk = [36])
class RequirementRowScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun capture(item: RequirementItem) {
        composeTestRule.setContent {
            BlueCardTheme { RequirementRow(item = item, onOpen = {}) }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun notCompleted() = capture(
        RequirementItem(
            "3",
            "Plan an overnight trek and find your way with a topo map.",
            Choice(1, 3),
            completed = false,
            markedByHand = false
        )
    )

    @Test
    fun completed() = capture(
        RequirementItem(
            "2",
            "Learn Leave No Trace and the Outdoor Code, and plan to follow them.",
            null,
            completed = true,
            markedByHand = true
        )
    )

    @Test
    fun notNeeded() = capture(
        RequirementItem(
            "3b",
            "Use a GPS receiver.",
            null,
            completed = false,
            markedByHand = true,
            notNeeded = true
        )
    )

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
