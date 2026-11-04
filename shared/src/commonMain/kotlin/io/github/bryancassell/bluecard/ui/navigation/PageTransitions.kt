package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEvent

// Pages slide the full width of their area, side by side, as Navigation 3's animation guide
// shows: https://developer.android.com/guide/navigation/navigation-3/animate-destinations
// SlideDirection.Start and End mirror the slides in a right-to-left layout. Navigation 3's
// defaults, a 700 ms crossfade and a 70% shrink on the back gesture, looked strange (#104).
// Material 3's transition patterns
// (https://m3.material.io/styles/motion/transitions/transition-patterns) say "Both Android and
// iOS should use platform defaults for forward and backward navigation" between "screens at
// consecutive levels of hierarchy", and "Sliding content the full width of the screen is
// excessive for a high frequency transition." The platform's slides move 96dp and fade, but on a
// Pixel 9 opening a page felt too short, the back swipe's fade looked bad, and a swipe from the
// right edge that didn't slide the page looked broken.
//
// FastOutSlowInEasing is tween's default, and what Material's first duration guidance
// (https://m1.material.io/motion/duration-easing.html) calls the standard curve. It says "Large,
// complex, full-screen transitions may have longer durations, occurring over 375ms", and
// "Transitions that exceed 400ms may feel too slow." The platform's activity slides take 450 ms
// with emphasized easing (fast_out_extra_slow_in), which moves a full-width slide 90% of the way
// in its first 170 ms, at up to 9dp per millisecond (77dp a frame at 120 Hz). On a Pixel 9,
// opening a page that way felt too fast, and a dropped frame showed as a jump. This slide gets
// 90% of the way in 237 ms, and peaks at about 3dp per millisecond.
private const val SLIDE_MILLIS = 375

private val slide: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = SLIDE_MILLIS, easing = FastOutSlowInEasing)

/** Opening a page: it slides in from the end, and the page it leaves slides out toward the start. */
fun AnimatedContentTransitionScope<*>.openPage(): ContentTransform =
    slideIntoContainer(SlideDirection.Start, slide) togetherWith
        slideOutOfContainer(SlideDirection.Start, slide)

/**
 * Going back, with Back or a released back swipe: the closing page slides out toward the end,
 * and the page returned to follows it.
 */
fun AnimatedContentTransitionScope<*>.closePage(): ContentTransform = slideToEnd(slide)

// NavDisplay seeks a swipe's transition to the swipe's progress, which iOS measures as the finger's
// travel over the screen's width. A linear slide keeps the pages under the finger, where the
// standard curve would leave them behind it and then ahead: 3% of the way at 10%, and 61% at 40%.
// The same curve finishes a swipe released far enough: NavDisplay 1.2.0 plays the rest of the
// transition with animateTo(), which takes no curve of its own, so the page carries on at one
// speed and stops at the edge. A swipe released short eases back, since NavDisplay animates the
// seek itself then. Following the finger was chosen over easing the finish (#351).
private val swipeSlide: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = SLIDE_MILLIS, easing = LinearEasing)

/**
 * Going back with a swipe that moves the pages, on iOS: the pages slide side by side, as they do
 * for [closePage], with the finger, away from the edge the swipe started at ([swipeEdge], a
 * NavigationEvent edge). That's toward the end in the layout's direction, since iOS starts a back
 * swipe at the start edge.
 */
fun AnimatedContentTransitionScope<*>.followBackSwipe(swipeEdge: Int): ContentTransform = slideTo(
    when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> SlideDirection.Right
        NavigationEvent.EDGE_RIGHT -> SlideDirection.Left
        else -> SlideDirection.End
    },
    swipeSlide
)

private fun AnimatedContentTransitionScope<*>.slideToEnd(
    spec: FiniteAnimationSpec<IntOffset>
): ContentTransform = slideTo(SlideDirection.End, spec)

private fun AnimatedContentTransitionScope<*>.slideTo(
    direction: SlideDirection,
    spec: FiniteAnimationSpec<IntOffset>
): ContentTransform =
    slideIntoContainer(direction, spec) togetherWith slideOutOfContainer(direction, spec)

/**
 * Onboarding and the rest of the app replacing each other: neither is a page of the other, so they
 * crossfade, in the 700 ms Navigation 3 crossfades by default on Android. Its default on iOS is a
 * slide like opening a page, which would make Home look as if Back could return to Onboarding.
 */
fun AnimatedContentTransitionScope<*>.crossfade(): ContentTransform =
    fadeIn(tween(CROSSFADE_MILLIS)) togetherWith fadeOut(tween(CROSSFADE_MILLIS))

private const val CROSSFADE_MILLIS = 700
