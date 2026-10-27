package io.github.bryancassell.bluecard.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Posts the task to the view, which runs it after the message being handled now. */
@Composable
internal actual fun rememberRunAfterCurrentEvent(): (task: () -> Unit) -> Unit {
    val view = LocalView.current
    return remember(view) { { task -> view.post(task) } }
}
