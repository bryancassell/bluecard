package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent

// The platform's transitions between activities, which Material 3 says to use for "screens at
// consecutive levels of hierarchy": https://m3.material.io/styles/motion/transitions/transition-patterns
// The values are from AOSP's activity_open_enter, activity_open_exit, activity_close_enter and
// activity_close_exit animations, which anim-ldrtl mirrors for right-to-left layouts:
// https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/anim/

private val SlideDistance = 96.dp

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

/** How far pages slide toward the end: right, or left in a right-to-left layout. */
private fun endwardDistance(density: Density, layoutDirection: LayoutDirection): Int {
    val distance = with(density) { SlideDistance.roundToPx() }
    return if (layoutDirection == LayoutDirection.Ltr) distance else -distance
}

private const val SLIDE_MILLIS = 450
private const val FADE_MILLIS = 83

private fun slide(): FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = SLIDE_MILLIS, easing = FastOutExtraSlowInEasing)

private fun <T> fade(delayMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis = FADE_MILLIS, delayMillis = delayMillis, easing = LinearEasing)

/**
 * Opening a page: it slides in from the end and fades in quickly, on top of the page it leaves,
 * which slides toward the start.
 */
fun openPage(density: Density, layoutDirection: LayoutDirection): ContentTransform {
    val distance = endwardDistance(density, layoutDirection)
    return ContentTransform(
        targetContentEnter = slideInHorizontally(slide()) { distance } +
            fadeIn(fade(delayMillis = 50)),
        initialContentExit = slideOutHorizontally(slide()) { -distance }
    )
}

/**
 * Going back: the closing page slides toward the end and fades out quickly, on top of the page
 * returned to, which slides in from the start.
 */
fun closePage(density: Density, layoutDirection: LayoutDirection): ContentTransform =
    closing(density, layoutDirection, fadeDelayMillis = 35, slidesAway = true)

/**
 * Swiping back: [closePage], played by the swipe, with two differences, both following the
 * platform's own swipe back between activities:
 * - A swipe can still be cancelled, so the page stays opaque until the swipe is halfway across.
 *   Released before then, it fades soon after, as the platform fades it once a swipe commits.
 * - A swipe from the end edge moves the finger toward the start, so the page doesn't slide
 *   against it.
 */
fun swipeBackPage(
    density: Density,
    layoutDirection: LayoutDirection,
    @NavigationEvent.SwipeEdge swipeEdge: Int
): ContentTransform {
    val endEdge = when (layoutDirection) {
        LayoutDirection.Ltr -> NavigationEvent.EDGE_RIGHT
        LayoutDirection.Rtl -> NavigationEvent.EDGE_LEFT
    }
    return closing(
        density,
        layoutDirection,
        fadeDelayMillis = SLIDE_MILLIS / 2,
        slidesAway = swipeEdge != endEdge
    )
}

private fun closing(
    density: Density,
    layoutDirection: LayoutDirection,
    fadeDelayMillis: Int,
    slidesAway: Boolean
): ContentTransform {
    val distance = endwardDistance(density, layoutDirection)
    return ContentTransform(
        targetContentEnter = slideInHorizontally(slide()) { -distance },
        initialContentExit = slideOutHorizontally(slide()) { if (slidesAway) distance else 0 } +
            fadeOut(fade(fadeDelayMillis)) +
            // Once it has faded out, the page shrinks to nothing until the slide ends, so it
            // stops taking touches and the page returned to gets them.
            shrinkOut(snap(delayMillis = fadeDelayMillis + FADE_MILLIS))
    )
}
