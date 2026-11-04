package io.github.bryancassell.bluecard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// How what the scout presses responds (PRD.md, iOS look and conventions). Android keeps Material's
// ripple. On iOS, a pressed button dims and a pressed row or card turns grey, as UIKit's do. The
// press effects are in common code, though only iOS uses them, so Android's Compose UI tests can
// check them: nothing runs Compose UI tests on iOS yet.

// UIKit's values, read from UIKit on the iOS 27.0 simulator. iOS's PressFeedbackTest checks them
// against UIKit, so a change in iOS fails it.

/** A system button's title while pressed (`UIButton` of type `.system`). */
internal const val BUTTON_PRESSED_ALPHA = 0.2f

/**
 * A filled button's background while pressed (`UIButton.Configuration.filled()`). Here it dims
 * the whole button, label too, which differs little at this alpha and needs no access to the
 * button's colors.
 */
internal const val FILLED_BUTTON_PRESSED_ALPHA = 0.75f

/** How long a press takes to fade once let go: iOS's default animation duration. */
internal const val PRESS_FADE_MILLIS = 250

/** A button's press feedback on iOS. A [filled] button's background dims less than text does. */
internal fun buttonPressDim(filled: Boolean) = PressDim(
    pressedAlpha = if (filled) FILLED_BUTTON_PRESSED_ALPHA else BUTTON_PRESSED_ALPHA,
    fadeMillis = PRESS_FADE_MILLIS
)

/**
 * A row's press feedback on iOS, in [color], the background UIKit gives a highlighted list row.
 * A row here draws its own background, such as `ListItem`'s, so rather than replacing it, the
 * color is multiplied over it in light mode and screened in dark mode. That gives exactly UIKit's
 * color over the white and black of UIKit's rows, darkens or lightens other backgrounds in
 * proportion, and keeps text readable. Dark text on a light card in dark mode, as on the rank
 * card, loses some contrast while held, by agreement (#352).
 */
internal fun rowPressHighlight(color: Color, darkTheme: Boolean) = PressHighlight(
    color = color,
    blendMode = if (darkTheme) BlendMode.Screen else BlendMode.Multiply,
    fadeMillis = PRESS_FADE_MILLIS
)

/**
 * Material's ripple without its press, which iOS's dim and highlight replace. Its focus and hover
 * layers stay, so a hardware keyboard's focus and a pointer still show.
 */
internal val FocusAndHoverRippleAlpha = RippleAlpha(
    pressedAlpha = 0f,
    focusedAlpha = RippleDefaults.RippleAlpha.focusedAlpha,
    draggedAlpha = RippleDefaults.RippleAlpha.draggedAlpha,
    hoveredAlpha = RippleDefaults.RippleAlpha.hoveredAlpha
)

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
 * such as the date picker.
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
 * over [fadeMillis], so a quick tap still shows. As a row's indication in the ripple's place, it
 * also shows focus and hover as the ripple does ([FocusAndHoverRippleAlpha]).
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
                // Drawn here rather than by delegating to Material's ripple node: of two draw
                // nodes delegated from one node, Compose 1.12 draws only the last.
                val stateLayer = stateLayerAlpha
                if (stateLayer > 0f) {
                    drawRect(currentValueOf(LocalContentColor), alpha = stateLayer)
                }
                val pressed = pressed
                if (pressed > 0f) drawRect(color, alpha = pressed, blendMode = blendMode)
            }
        }

    override fun equals(other: Any?) = other is PressHighlight &&
        other.color == color && other.blendMode == blendMode && other.fadeMillis == fadeMillis

    override fun hashCode() = (31 * color.hashCode() + blendMode.hashCode()) * 31 + fadeMillis
}

/**
 * Follows [interactionSource]: [pressed] is 1 from a press, and falls to 0 over [fadeMillis] once
 * it ends. [stateLayerAlpha] is the ripple's for focus or hover, or 0.
 */
private abstract class PressNode(
    private val interactionSource: InteractionSource,
    private val fadeMillis: Int
) : Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private var animatable = Animatable(0f)
    private var stateLayer = mutableFloatStateOf(0f)

    // Read in draw, which Compose observes, so a change redraws.
    protected val pressed: Float get() = animatable.value
    protected val stateLayerAlpha: Float get() = stateLayer.floatValue

    override fun onAttach() {
        // A node can be detached part way through a fade and attached again, as a button's is
        // when its item in a lazy list scrolls away and back, so each attach starts afresh.
        val animatable = Animatable(0f).also { animatable = it }
        val stateLayer = mutableFloatStateOf(0f).also { stateLayer = it }
        val presses = mutableSetOf<PressInteraction.Press>()
        val focuses = mutableSetOf<FocusInteraction.Focus>()
        val hovers = mutableSetOf<HoverInteraction.Enter>()
        var fade: Job? = null
        interactionSource.interactions.onEach { interaction ->
            when (interaction) {
                is PressInteraction.Press -> presses += interaction
                is PressInteraction.Release -> presses -= interaction.press
                is PressInteraction.Cancel -> presses -= interaction.press
                is FocusInteraction.Focus -> focuses += interaction
                is FocusInteraction.Unfocus -> focuses -= interaction.focus
                is HoverInteraction.Enter -> hovers += interaction
                is HoverInteraction.Exit -> hovers -= interaction.enter
            }
            if (interaction !is PressInteraction) {
                // Focus over hover, as in the ripple.
                stateLayer.floatValue = when {
                    focuses.isNotEmpty() -> FocusAndHoverRippleAlpha.focusedAlpha
                    hovers.isNotEmpty() -> FocusAndHoverRippleAlpha.hoveredAlpha
                    else -> 0f
                }
            } else if (presses.isNotEmpty()) {
                // In turn, before the next interaction: a quick tap's release follows its press
                // at once, and its fade must start from the press. A fade not yet started would
                // otherwise run after the snap.
                fade?.cancel()
                animatable.snapTo(1f)
            } else {
                // On its own, so a press can stop it.
                fade = coroutineScope.launch { animatable.animateTo(0f, tween(fadeMillis)) }
            }
        }.launchIn(coroutineScope)
    }
}
