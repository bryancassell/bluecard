package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Queues the task on the main queue, which runs it after the event being handled now. */
@Composable
internal actual fun rememberRunAfterCurrentEvent(): (task: () -> Unit) -> Unit =
    remember { { task -> dispatch_async(dispatch_get_main_queue(), task) } }
