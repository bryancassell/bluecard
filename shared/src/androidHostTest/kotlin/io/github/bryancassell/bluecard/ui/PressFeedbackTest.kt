package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.testing.assertColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// What iOS draws, drawn here since Compose UI tests don't run on iOS yet. The colors are UIKit's
// highlighted row in light and dark mode, which iOS reads from UIKit (rowHighlightColor).
private const val FADE_MILLIS = PRESS_FADE_MILLIS.toLong()
private val Dim = buttonPressDim(filled = false)
private val LightHighlight = rowPressHighlight(Color(0xFFD1D1D6), darkTheme = false)
private val DarkHighlight = rowPressHighlight(Color(0xFF3A3A3C), darkTheme = true)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PressFeedbackTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val interactionSource = MutableInteractionSource()

    /** Shows a square of [color] on a white page, with [indication]. */
    private fun show(indication: Indication, color: Color) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(Modifier.background(Color.White)) {
                Box(
                    Modifier
                        .size(20.dp)
                        .indication(interactionSource, indication)
                        .background(color)
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun press(): PressInteraction.Press {
        val press = PressInteraction.Press(Offset.Zero)
        assertTrue(interactionSource.tryEmit(press))
        composeTestRule.mainClock.advanceTimeByFrame()
        return press
    }

    private fun release(press: PressInteraction.Press) {
        assertTrue(interactionSource.tryEmit(PressInteraction.Release(press)))
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun shown(): Color = composeTestRule.onRoot().captureToImage().toPixelMap().let {
        it[it.width / 2, it.height / 2]
    }

    private fun assertShown(expected: Color) = assertColor(expected, shown())

    /** The color in the middle of the node tagged [tag]. */
    private fun shownIn(tag: String): Color =
        composeTestRule.onNodeWithTag(tag).captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }

    @Test
    fun dim_drawsAsIs_untilPressed() {
        show(Dim, Color.Black)

        assertShown(Color.Black)
    }

    @Test
    fun dim_whilePressed_drawsAtThePressedAlpha() {
        show(Dim, Color.Black)

        press()

        // Black at 0.2 over the white page.
        assertShown(Color(0xFFCCCCCC))
    }

    @Test
    fun dim_onceLetGo_fadesBackOverTheFade() {
        show(Dim, Color.Black)
        release(press())

        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS / 2)
        val halfway = shown()
        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS)

        assertTrue("$halfway", halfway.red > 0.05f && halfway.red < 0.75f)
        assertShown(Color.Black)
    }

    @Test
    fun dim_aQuickTap_stillShows() {
        show(Dim, Color.Black)
        val press = PressInteraction.Press(Offset.Zero)
        interactionSource.tryEmit(press)
        interactionSource.tryEmit(PressInteraction.Release(press))

        composeTestRule.mainClock.advanceTimeByFrame()

        assertNotEquals(Color.Black, shown())
    }

    @Test
    fun dim_pressedAgainWhileFading_isFullyDimmedAgain() {
        show(Dim, Color.Black)
        release(press())
        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS / 2)

        press()

        assertShown(Color(0xFFCCCCCC))
    }

    @Test
    fun dim_aQuickTapWhileFading_isFullyDimmedAgain() {
        // A quick tap in a scrolling page arrives as a press and its release together.
        show(Dim, Color.Black)
        release(press())
        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS / 2)
        val press = PressInteraction.Press(Offset.Zero)
        interactionSource.tryEmit(press)
        interactionSource.tryEmit(PressInteraction.Release(press))

        composeTestRule.mainClock.advanceTimeByFrame()

        assertShown(Color(0xFFCCCCCC))
    }

    @Test
    fun filledDim_whilePressed_drawsAtItsAlpha() {
        show(buttonPressDim(filled = true), Color.Black)

        press()

        // Black at 0.75 over the white page.
        assertShown(Color(0xFF404040))
    }

    @Test
    fun dim_aCancelledPress_fadesToo() {
        show(Dim, Color.Black)
        val press = press()

        interactionSource.tryEmit(PressInteraction.Cancel(press))
        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS + 16)

        assertShown(Color.Black)
    }

    @Test
    fun dim_focus_isNotAPress() {
        // As when a hardware keyboard's Tab reaches a button.
        show(Dim, Color.Black)

        assertTrue(interactionSource.tryEmit(FocusInteraction.Focus()))
        composeTestRule.mainClock.advanceTimeByFrame()

        assertShown(Color.Black)
    }

    @Test
    fun lightHighlight_onWhite_isItsColor() {
        show(LightHighlight, Color.White)

        press()

        assertShown(Color(0xFFD1D1D6))
    }

    @Test
    fun lightHighlight_keepsDarkTextDark() {
        show(LightHighlight, Color.Black)

        press()

        assertShown(Color.Black)
    }

    @Test
    fun darkHighlight_onBlack_isItsColor() {
        show(DarkHighlight, Color.Black)

        press()

        assertShown(Color(0xFF3A3A3C))
    }

    @Test
    fun darkHighlight_keepsLightTextLight() {
        show(DarkHighlight, Color.White)

        press()

        assertShown(Color.White)
    }

    @Test
    fun highlight_focused_showsTheRipplesFocusLayer() {
        // As the ripple it takes the place of did: the content color at Material's focus alpha.
        show(LightHighlight, Color.White)

        assertTrue(interactionSource.tryEmit(FocusInteraction.Focus()))
        composeTestRule.mainClock.advanceTimeBy(100)

        val focused = 1f - FocusAndHoverRippleAlpha.focusedAlpha
        assertShown(Color(focused, focused, focused))
    }

    @Test
    fun highlight_hovered_showsTheRipplesHoverLayer() {
        show(LightHighlight, Color.White)

        assertTrue(interactionSource.tryEmit(HoverInteraction.Enter()))
        composeTestRule.mainClock.advanceTimeByFrame()

        val hovered = 1f - FocusAndHoverRippleAlpha.hoveredAlpha
        assertShown(Color(hovered, hovered, hovered))
    }

    @Test
    fun highlight_focusedThenHovered_showsTheLatest() {
        // As the ripple's state layer does.
        show(LightHighlight, Color.White)

        interactionSource.tryEmit(FocusInteraction.Focus())
        interactionSource.tryEmit(HoverInteraction.Enter())
        composeTestRule.mainClock.advanceTimeByFrame()

        val hovered = 1f - FocusAndHoverRippleAlpha.hoveredAlpha
        assertShown(Color(hovered, hovered, hovered))
    }

    @Test
    fun highlight_focusAndHoverEnded_showsNoLayer() {
        show(LightHighlight, Color.White)
        val focus = FocusInteraction.Focus()
        val enter = HoverInteraction.Enter()
        interactionSource.tryEmit(focus)
        interactionSource.tryEmit(enter)

        interactionSource.tryEmit(FocusInteraction.Unfocus(focus))
        interactionSource.tryEmit(HoverInteraction.Exit(enter))
        composeTestRule.mainClock.advanceTimeByFrame()

        assertShown(Color.White)
    }

    @Test
    fun highlight_onceLetGo_fadesAway() {
        show(LightHighlight, Color.White)
        release(press())

        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS + 16)

        assertShown(Color.White)
    }

    @Test
    fun focusAndHoverHighlight_pressed_drawsNothing() {
        // A switch row's, which iOS's own apps don't turn grey while held. That it ignores the
        // press, so as not to redraw, isn't tested: Robolectric draws only when captured, so
        // there's no redraw to count.
        show(FocusAndHoverHighlight, Color.White)

        press()

        assertShown(Color.White)
    }

    @Test
    fun focusAndHoverHighlight_focused_showsTheRipplesFocusLayer() {
        show(FocusAndHoverHighlight, Color.White)

        assertTrue(interactionSource.tryEmit(FocusInteraction.Focus()))
        composeTestRule.mainClock.advanceTimeBy(100)

        val focused = 1f - FocusAndHoverRippleAlpha.focusedAlpha
        assertShown(Color(focused, focused, focused))
    }

    @Test
    fun theSameFeedback_isEqual() {
        // Compose compares indications to decide whether to make new nodes.
        assertEquals(Dim, PressDim(0.2f, FADE_MILLIS.toInt()))
        assertEquals(Dim.hashCode(), PressDim(0.2f, FADE_MILLIS.toInt()).hashCode())
        assertNotEquals(Dim, PressDim(0.75f, FADE_MILLIS.toInt()))
        assertEquals(
            LightHighlight,
            PressHighlight(Color(0xFFD1D1D6), BlendMode.Multiply, FADE_MILLIS.toInt())
        )
        assertEquals(
            LightHighlight.hashCode(),
            PressHighlight(Color(0xFFD1D1D6), BlendMode.Multiply, FADE_MILLIS.toInt()).hashCode()
        )
        assertNotEquals(LightHighlight, DarkHighlight)
    }

    /** A black Material button with iOS's press feedback, on a white page. */
    private fun showIosButton(): InputModeManager {
        lateinit var inputModeManager: InputModeManager
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            inputModeManager = LocalInputModeManager.current
            Box(Modifier.background(Color.White).padding(8.dp)) {
                IosButtonPressFeedback(filled = true) { interactionSource, feedback ->
                    Button(
                        onClick = {},
                        modifier = feedback.testTag("button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        ),
                        interactionSource = interactionSource
                    ) {}
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        return inputModeManager
    }

    @Test
    fun iosButton_pressed_dims() {
        showIosButton()

        composeTestRule.onNodeWithTag("button").performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(100)

        // Black at 0.75 over the white page.
        assertColor(Color(0xFF404040), shownIn("button"))
    }

    // Android draws a ripple's press in a View of its own, which captureToImage leaves out, so
    // the tests read the ripple's configuration instead.
    @Test
    fun iosButton_ripple_showsNoPress() {
        var configuration: RippleConfiguration? = null
        composeTestRule.setContent {
            IosButtonPressFeedback(filled = false) { _, _ ->
                configuration = LocalRippleConfiguration.current
            }
        }

        composeTestRule.runOnIdle {
            assertEquals(FocusAndHoverRippleAlpha, configuration?.rippleAlpha)
            assertEquals(0f, FocusAndHoverRippleAlpha.pressedAlpha)
        }
    }

    @Test
    fun iosButton_focused_keepsTheRipplesFocusLayer() {
        val inputModeManager = showIosButton()
        composeTestRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }

        composeTestRule.onNodeWithTag("button").requestFocus()
        composeTestRule.mainClock.advanceTimeBy(100)

        // The white content color at Material's focus alpha, over the black button.
        val focused = FocusAndHoverRippleAlpha.focusedAlpha
        assertColor(Color(focused, focused, focused), shownIn("button"))
    }

    @Test
    fun iosRow_pressed_isHighlightedThroughTheTheme() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            ProvideIosRowHighlight(LightHighlight) {
                Box(Modifier.size(40.dp).background(Color.White).clickable {}.testTag("row"))
            }
        }

        composeTestRule.onNodeWithTag("row").performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(100)

        assertColor(Color(0xFFD1D1D6), shownIn("row"))
    }
}
