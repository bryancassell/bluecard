package io.github.bryancassell.bluecard.ui

import android.util.Log
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TaskFailure"

/**
 * A task that failed, such as saving progress or creating a report, for the screen to tell the
 * scout about. Each failure is its own object, not a data class, so one that follows another is
 * told apart from it even if the screen never saw the first one cleared.
 */
class TaskFailure

/**
 * Runs a ViewModel's tasks that can fail, such as saving progress or creating a report to
 * share or save, in [scope]. Keeps the latest one that failed until its screen has shown it
 * with [TaskFailedSnackbarHost] or [TaskFailureSnackbar].
 */
class TaskRunner(private val scope: CoroutineScope) {
    private val _failure = MutableStateFlow<TaskFailure?>(null)

    /** The latest task that failed, or null once the screen has shown it. */
    val failure: StateFlow<TaskFailure?> = _failure.asStateFlow()

    /**
     * Launches [task]. If it throws an [IOException], which repositories throw when they can't
     * read or save the scout's data or write a report, logs it and reports it in [failure]. Any
     * other exception is a bug, so it still crashes the app, as in [catchLoadFailure].
     */
    fun launch(task: suspend () -> Unit): Job = scope.launch {
        try {
            task()
        } catch (e: IOException) {
            // The app reports caught exceptions nowhere else, so logcat and bug reports are
            // the only way to tell what failed and why.
            Log.w(TAG, "Task failed", e)
            _failure.value = TaskFailure()
        }
    }

    /** The screen has shown [shown]. A failure since then stays, to be shown next. */
    fun onShown(shown: TaskFailure) {
        _failure.compareAndSet(shown, null)
    }
}

/**
 * Shows a snackbar with [message] for each [failure], as [MessageSnackbarHost] does. Unless
 * it's given, the message says progress couldn't be saved, as most tasks are saves.
 */
@Composable
fun TaskFailedSnackbarHost(
    failure: TaskFailure?,
    onShown: (TaskFailure) -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.save_failed)
) {
    MessageSnackbarHost(failure, message, onShown, modifier)
}

/**
 * Shows [failure] in [hostState] as [TaskFailedSnackbarHost] does, for a screen that has more
 * than one kind of failure, each with its own [message], to show in one host: [hostState]
 * shows one snackbar at a time, as Material asks, and queues the rest. One still waiting its
 * turn when the screen goes is dropped, as one showing is.
 */
@Composable
fun TaskFailureSnackbar(
    failure: TaskFailure?,
    onShown: (TaskFailure) -> Unit,
    hostState: SnackbarHostState,
    message: String = stringResource(R.string.save_failed)
) {
    MessageSnackbar(failure, message, onShown, hostState)
}

/**
 * Shows a snackbar with [text] for each [message] from UI state, then calls [onShown] once it's
 * gone, or the screen is, so the ViewModel can clear it. That's how the UI layer guide has the
 * UI show a message from UI state:
 * https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events
 *
 * Each message must be its own object, as a [TaskFailure] is, so one that follows another is
 * shown too, even if it's the same kind.
 */
@Composable
fun <T : Any> MessageSnackbarHost(
    message: T?,
    text: String,
    onShown: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val hostState = remember { SnackbarHostState() }
    MessageSnackbar(message, text, onShown, hostState)
    SnackbarHost(hostState, modifier)
}

/** Shows [message] in [hostState] as [MessageSnackbarHost] does. */
@Composable
private fun <T : Any> MessageSnackbar(
    message: T?,
    text: String,
    onShown: (T) -> Unit,
    hostState: SnackbarHostState
) {
    if (message != null) {
        val currentOnShown by rememberUpdatedState(onShown)
        // Keyed by the message, so one that replaces another is shown too.
        LaunchedEffect(message) {
            try {
                hostState.showSnackbar(text)
            } finally {
                // Also once the screen is gone, such as when the scout leaves it while the
                // message shows, so the old message doesn't come back when they return.
                currentOnShown(message)
            }
        }
    }
}
