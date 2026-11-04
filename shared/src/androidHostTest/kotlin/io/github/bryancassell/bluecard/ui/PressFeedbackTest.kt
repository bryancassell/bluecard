package io.github.bryancassell.bluecard.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS's values (PressFeedback.ios.kt), drawn here since Compose UI tests don't run on iOS yet.
private const val FADE_MILLIS = 250L
private val Dim = PressDim(pressedAlpha = 0.2f, fadeMillis = FADE_MILLIS.toInt())
private val LightHighlight =
    PressHighlight(Color(0xFFD1D1D6), BlendMode.Multiply, FADE_MILLIS.toInt())
private val DarkHighlight = PressHighlight(Color(0xFF3A3A3C), BlendMode.Screen, FADE_MILLIS.toInt())

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

    private fun assertShown(expected: Color) {
        val shown = shown()
        // One step of 8-bit color either way, from rounding.
        val step = 1.5f / 255
        listOf(
            expected.red to shown.red,
            expected.green to shown.green,
            expected.blue to shown.blue
        ).forEach { (want, got) -> assertEquals("$expected, shown $shown", want, got, step) }
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
    fun dim_aCancelledPress_fadesToo() {
        show(Dim, Color.Black)
        val press = press()

        interactionSource.tryEmit(PressInteraction.Cancel(press))
        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS + 16)

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
    fun highlight_onceLetGo_fadesAway() {
        show(LightHighlight, Color.White)
        release(press())

        composeTestRule.mainClock.advanceTimeBy(FADE_MILLIS + 16)

        assertShown(Color.White)
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
}
