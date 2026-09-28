package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The one-argument version of `dropUnlessResumed` from lifecycle-runtime-compose: returns a
 * callback that runs [block] only while [lifecycleOwner] is resumed, and drops the call
 * otherwise.
 *
 * NavDisplay holds each screen at STARTED until its enter or exit animation finishes, so a
 * navigation callback wrapped in this ignores taps on a screen that is leaving, such as the
 * second tap of a quick double tap.
 */
@Composable
fun <T> dropUnlessResumed(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    block: (T) -> Unit
): (T) -> Unit = { value ->
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value)
}
