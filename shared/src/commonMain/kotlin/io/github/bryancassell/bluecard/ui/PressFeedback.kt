package io.github.bryancassell.bluecard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// How what the scout presses responds (PRD.md, iOS look and conventions). Android keeps Material's
// ripple. On iOS, a pressed button dims and a pressed row or card turns grey, as UIKit's do. The
// press effects are in common code, though only iOS uses them, so Android's Compose UI tests can
// check them: nothing runs Compose UI tests on iOS yet.

/**
 * Gives what's clickable with the theme's indication, such as a row or a card, the platform's
 * press feedback. `BlueCardTheme` puts it around the app.
 */
@Composable
internal expect fun ProvidePressFeedback(darkTheme: Boolean, content: @Composable () -> Unit)

/**
 * The platform's press feedback for a Material button, given to [content] as the interaction
 * source and modifier to pass the button, or null and no modifier to keep Material's. A
 * [filled] button has a background, which iOS dims less than a button's text.
 */
@Composable
internal expect fun ButtonPressFeedback(
    filled: Boolean,
    content: @Composable (interactionSource: MutableInteractionSource?, modifier: Modifier) -> Unit
)

/**
 * The platform's press feedback for Material components in [content] that draw their own ripple,
 * such as the date picker's days.
 */
@Composable
internal expect fun MaterialPressFeedback(content: @Composable () -> Unit)

/**
 * Draws what it's on at [pressedAlpha] while pressed. Once let go, it fades back over
 * [fadeMillis], so a quick tap still shows.
 */
class PressDim(private val pressedAlpha: Float, private val fadeMillis: Int) :
    IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : PressNode(interactionSource, fadeMillis) {
            private val paint = Paint()

            override fun ContentDrawScope.draw() {
                val pressed = pressed
                if (pressed == 0f) {
                    drawContent()
                    return
                }
                paint.alpha = 1f - pressed * (1f - pressedAlpha)
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                    drawContent()
                    canvas.restore()
                }
            }
        }

    override fun equals(other: Any?) = other is PressDim &&
        other.pressedAlpha == pressedAlpha && other.fadeMillis == fadeMillis

    override fun hashCode() = 31 * pressedAlpha.hashCode() + fadeMillis
}

/**
 * Blends [color] over what it's on, with [blendMode], while pressed. Once let go, it fades back
 * over [fadeMillis], so a quick tap still shows.
 */
class PressHighlight(
    private val color: Color,
    private val blendMode: BlendMode,
    private val fadeMillis: Int
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : PressNode(interactionSource, fadeMillis) {
            override fun ContentDrawScope.draw() {
                drawContent()
                val pressed = pressed
                if (pressed > 0f) drawRect(color, alpha = pressed, blendMode = blendMode)
            }
        }

    override fun equals(other: Any?) = other is PressHighlight &&
        other.color == color && other.blendMode == blendMode && other.fadeMillis == fadeMillis

    override fun hashCode() = (31 * color.hashCode() + blendMode.hashCode()) * 31 + fadeMillis
}

/**
 * Follows [interactionSource]'s presses: [pressed] is 1 from a press, and falls to 0 over
 * [fadeMillis] once it ends.
 */
private abstract class PressNode(
    private val interactionSource: InteractionSource,
    private val fadeMillis: Int
) : Modifier.Node(),
    DrawModifierNode {
    private val animatable = Animatable(0f)

    protected val pressed: Float get() = animatable.value

    override fun onAttach() {
        val presses = mutableSetOf<PressInteraction.Press>()
        interactionSource.interactions.onEach { interaction ->
            when (interaction) {
                is PressInteraction.Press -> presses += interaction
                is PressInteraction.Release -> presses -= interaction.press
                is PressInteraction.Cancel -> presses -= interaction.press
                else -> return@onEach
            }
            // Read now: a quick tap's release can arrive before this runs, and the press must
            // still show. The animatable stops a fade still running when a press snaps it back.
            val isPressed = presses.isNotEmpty()
            coroutineScope.launch {
                if (isPressed) {
                    animatable.snapTo(1f)
                    invalidateDraw()
                } else {
                    animatable.animateTo(0f, tween(fadeMillis)) { invalidateDraw() }
                }
            }
        }.launchIn(coroutineScope)
    }
}
