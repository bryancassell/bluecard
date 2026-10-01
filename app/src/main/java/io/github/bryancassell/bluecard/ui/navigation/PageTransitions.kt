package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

// Pages slide the full width of their area, side by side, as Navigation 3's animation guide
// shows: https://developer.android.com/guide/navigation/navigation-3/animate-destinations
// SlideDirection.Start and End mirror the slides in a right-to-left layout. PRD.md (Design
// decisions) says why BlueCard doesn't use the platform's shorter slides, as Material 3 advises.
//
// FastOutSlowInEasing is tween's default, and what Material's first duration guidance
// (https://m1.material.io/motion/duration-easing.html) calls the standard curve. It says "Large,
// complex, full-screen transitions may have longer durations, occurring over 375ms", and
// "Transitions that exceed 400ms may feel too slow." The platform's activity slides take 450 ms
// with emphasized easing (fast_out_extra_slow_in), which moves a full-width slide 90% of the way
// in its first 170 ms, at up to 9dp per millisecond (77dp a frame at 120 Hz). On a Pixel 9,
// opening a page that way felt too fast, and a dropped frame showed as a jump. This slide gets
// 90% of the way in 237 ms, and peaks at about 3dp per millisecond.
private val slide: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = 375, easing = FastOutSlowInEasing)

/** Opening a page: it slides in from the end, and the page it leaves slides out toward the start. */
fun AnimatedContentTransitionScope<*>.openPage(): ContentTransform =
    slideIntoContainer(SlideDirection.Start, slide) togetherWith
        slideOutOfContainer(SlideDirection.Start, slide)

/**
 * Going back, with Back or a released back swipe: the closing page slides out toward the end,
 * and the page returned to follows it.
 */
fun AnimatedContentTransitionScope<*>.closePage(): ContentTransform =
    slideIntoContainer(SlideDirection.End, slide) togetherWith
        slideOutOfContainer(SlideDirection.End, slide)
