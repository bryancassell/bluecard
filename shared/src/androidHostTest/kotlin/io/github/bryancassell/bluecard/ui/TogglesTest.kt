package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// iOS's form of the rows, shown here since Compose UI tests don't run on iOS yet. Material's switch
// stands in for UIKit's, which only iOS can draw. Android's form is tested on the screens that use
// the rows.

private const val LONG_LABEL = "Clean and adjust a bike, then take it on a ride of at least ten " +
    "miles with a counselor watching"

// Native graphics measure text as a phone does, so a long label wraps.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TogglesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val SemanticsNodeInteraction.bounds: Rect get() = fetchSemanticsNode().boundsInRoot

    private fun switch() = composeTestRule.onNodeWithTag(TOGGLE_SWITCH_TAG, useUnmergedTree = true)

    /** The toggle's label, which a test can change after showing it. */
    private var label by mutableStateOf("Completed")

    private fun showIosToggle() {
        composeTestRule.setContent {
            var checked by remember { mutableStateOf(false) }
            ToggleRow(label, checked, onCheckedChange = { checked = it }, iosSwitch = true)
        }
    }

    @Test
    fun iosToggle_isOneSwitch_thatTappingItsLabelToggles() {
        showIosToggle()

        // Merged into one node, which screen readers read.
        composeTestRule.onNodeWithText("Completed")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun iosToggle_hasItsSwitchAtTheRowsEnd() {
        showIosToggle()

        val row = composeTestRule.onNodeWithText("Completed").bounds
        val text = composeTestRule.onNodeWithText("Completed", useUnmergedTree = true).bounds
        val switch = switch().bounds
        val padding = with(composeTestRule.density) { 16.dp.toPx() }
        assertEquals(row.right - padding, switch.right, 0.5f)
        assertEquals(row.left + padding, text.left, 0.5f)
        assertTrue("The switch is after the label", switch.left > text.right)
    }

    @Test
    fun iosToggle_withALongLabel_wrapsItBesideTheSwitch() {
        showIosToggle()
        val shortSwitch = switch().bounds
        val oneLine = composeTestRule.onNodeWithText("Completed", useUnmergedTree = true).bounds

        label = LONG_LABEL

        val longLabel = composeTestRule.onNodeWithText(LONG_LABEL, useUnmergedTree = true).bounds
        assertTrue("The label wraps", longLabel.height > 2 * oneLine.height)
        assertTrue("The label ends before the switch", longLabel.right < switch().bounds.left)
        assertEquals(shortSwitch.width, switch().bounds.width, 0.5f)
        assertEquals(shortSwitch.right, switch().bounds.right, 0.5f)
    }

    /**
     * Shows a toggle on a white page with iOS's row highlight as the theme's, as `BlueCardTheme`
     * gives it on iOS. Its focus layer is checked on the indication, in PressFeedbackTest: a test
     * focusing the row here passed or failed with the order the tests ran in, though the row was
     * focused, in keyboard mode.
     */
    private fun showOnWhiteWithRowHighlight(iosSwitch: Boolean) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            ProvideIosRowHighlight(rowPressHighlight(Color(0xFFD1D1D6), darkTheme = false)) {
                Box(Modifier.background(Color.White)) {
                    ToggleRow("Completed", checked = false, onCheckedChange = {}, iosSwitch)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    /** The color at the row's start, in its padding, where nothing else draws. */
    private fun rowStart(): Color = composeTestRule.onNodeWithText("Completed").captureToImage()
        .toPixelMap().let { it[2, it.height / 2] }

    private fun assertColor(expected: Color, shown: Color) {
        // One step of 8-bit color either way, from rounding.
        val step = 1.5f / 255
        listOf(
            expected.red to shown.red,
            expected.green to shown.green,
            expected.blue to shown.blue
        ).forEach { (want, got) -> assertEquals("$expected, shown $shown", want, got, step) }
    }

    private fun pressRow() {
        composeTestRule.onNodeWithText("Completed").performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(100)
    }

    @Test
    fun iosToggle_held_doesntTurnGrey() {
        // As iOS's own switch rows don't. The grey would also cover UIKit's switch.
        showOnWhiteWithRowHighlight(iosSwitch = true)

        pressRow()

        assertColor(Color.White, rowStart())
    }

    @Test
    fun checkboxToggle_held_turnsGreyWithTheTheme() {
        // So the test above would see the grey if it were drawn.
        showOnWhiteWithRowHighlight(iosSwitch = false)

        pressRow()

        assertColor(Color(0xFFD1D1D6), rowStart())
    }

    private fun showIosChoices(firstLabel: String = "This phone") {
        composeTestRule.setContent {
            var chosen by remember { mutableIntStateOf(0) }
            Column(Modifier.selectableGroup()) {
                listOf(firstLabel, "The file").forEachIndexed { index, label ->
                    ChoiceRow(
                        selected = chosen == index,
                        onClick = { chosen = index },
                        iosCheckmark = true
                    ) { Text(label) }
                }
            }
        }
    }

    private fun checkmark() =
        composeTestRule.onNodeWithTag(CHOICE_CHECKMARK_TAG, useUnmergedTree = true)

    @Test
    fun iosChoices_markOnlyTheChosenOne_atItsRowsEnd() {
        showIosChoices()

        composeTestRule.onAllNodesWithTag(CHOICE_CHECKMARK_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
        val phone = composeTestRule.onNodeWithText("This phone")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        assertEquals(phone.bounds.right, checkmark().bounds.right, 0.5f)
        assertTrue(phone.bounds.contains(checkmark().bounds.center))

        val file = composeTestRule.onNodeWithText("The file").performClick().assertIsSelected()
        phone.assertIsNotSelected()
        composeTestRule.onAllNodesWithTag(CHOICE_CHECKMARK_TAG, useUnmergedTree = true)
            .assertCountEquals(1)
        assertTrue(file.bounds.contains(checkmark().bounds.center))
    }

    @Test
    fun iosChoice_keepsItsTextsWidth_whenChosen() {
        showIosChoices(firstLabel = LONG_LABEL)
        val text = composeTestRule.onNodeWithText(LONG_LABEL, useUnmergedTree = true)
        val chosen = text.bounds

        composeTestRule.onNodeWithText("The file").performClick()

        assertEquals(chosen, text.bounds)
    }
}
