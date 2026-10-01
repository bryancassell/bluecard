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
// SlideDirection.Start and End mirror the slides in a right-to-left layout. ARCHITECTURE.md says
// why BlueCard doesn't use the platform's shorter slides, as Material 3 advises, or their easing.
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
