package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

// Pages slide the full width of their area, side by side, as Navigation 3's animation guide
// shows: https://developer.android.com/guide/navigation/navigation-3/animate-destinations
// SlideDirection.Start and End mirror the slides in a right-to-left layout. ARCHITECTURE.md says
// why BlueCard doesn't use the platform's shorter slides, as Material 3 advises.

// The platform's fast_out_extra_slow_in interpolator, Material's emphasized easing, is the path
// M 0,0 C 0.05,0 0.133333,0.06 0.166666,0.4 C 0.208333,0.82 0.25,1 1,1. Compose's PathEasing
// needs a native library that Robolectric can't load, so each of the path's two cubic curves is
// a CubicBezierEasing scaled into its part of the square, which draws the same curve.
private const val JOIN_X = 0.166666f
private const val JOIN_Y = 0.4f
private val BeforeJoin =
    CubicBezierEasing(0.05f / JOIN_X, 0f, 0.133333f / JOIN_X, 0.06f / JOIN_Y)
private val AfterJoin = CubicBezierEasing(
    (0.208333f - JOIN_X) / (1f - JOIN_X),
    (0.82f - JOIN_Y) / (1f - JOIN_Y),
    (0.25f - JOIN_X) / (1f - JOIN_X),
    1f
)
internal val FastOutExtraSlowInEasing = Easing { x ->
    if (x < JOIN_X) {
        JOIN_Y * BeforeJoin.transform(x / JOIN_X)
    } else {
        JOIN_Y + (1f - JOIN_Y) * AfterJoin.transform((x - JOIN_X) / (1f - JOIN_X))
    }
}

private fun slide(easing: Easing = FastOutExtraSlowInEasing): FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = 450, easing = easing)

/** Opening a page: it slides in from the end, and the page it leaves slides out toward the start. */
fun AnimatedContentTransitionScope<*>.openPage(): ContentTransform =
    slideIntoContainer(SlideDirection.Start, slide()) togetherWith
        slideOutOfContainer(SlideDirection.Start, slide())

/** Going back: the closing page slides out toward the end, and the page returned to follows it. */
fun AnimatedContentTransitionScope<*>.closePage(): ContentTransform = closing(slide())

/**
 * Swiping back, from either edge: [closePage], played by the swipe. The slide is linear, so the
 * pages move with the finger. Once the swipe is released, NavDisplay eases the rest of the way.
 */
fun AnimatedContentTransitionScope<*>.swipeBackPage(): ContentTransform =
    closing(slide(LinearEasing))

private fun AnimatedContentTransitionScope<*>.closing(
    slide: FiniteAnimationSpec<IntOffset>
): ContentTransform = slideIntoContainer(SlideDirection.End, slide) togetherWith
    slideOutOfContainer(SlideDirection.End, slide)
