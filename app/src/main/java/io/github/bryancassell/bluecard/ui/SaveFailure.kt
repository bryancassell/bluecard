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

private const val TAG = "SaveFailure"

/**
 * A save that failed, for the screen to tell the scout about. Each failure is its own object,
 * not a data class, so one that follows another is told apart from it even if the screen
 * never saw the first one cleared.
 */
class SaveFailure

/**
 * Runs a ViewModel's saves in [scope], and keeps the latest one that failed until its screen
 * has shown it with [SaveFailedSnackbarHost].
 */
class SaveRunner(private val scope: CoroutineScope) {
    private val _failure = MutableStateFlow<SaveFailure?>(null)

    /** The latest save that failed, or null once the screen has shown it. */
    val failure: StateFlow<SaveFailure?> = _failure.asStateFlow()

    /**
     * Launches [save]. If it throws an [IOException], which repositories throw when data can't
     * be saved, logs it and reports it in [failure]. Any other exception is a bug, so it still
     * crashes the app, as in [catchLoadFailure].
     */
    fun launch(save: suspend () -> Unit): Job = scope.launch {
        try {
            save()
        } catch (e: IOException) {
            // The app reports caught exceptions nowhere else, so logcat and bug reports are
            // the only way to tell what failed and why.
            Log.w(TAG, "Couldn't save", e)
            _failure.value = SaveFailure()
        }
    }

    /** The screen has shown [shown]. A failure since then stays, to be shown next. */
    fun onShown(shown: SaveFailure) {
        _failure.compareAndSet(shown, null)
    }
}

/**
 * Shows a snackbar saying progress couldn't be saved for each [failure], then calls [onShown]
 * once it's gone, or the screen is, so the ViewModel can clear it. That's how the UI layer
 * guide has the UI show a message from UI state:
 * https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events
 */
@Composable
fun SaveFailedSnackbarHost(
    failure: SaveFailure?,
    onShown: (SaveFailure) -> Unit,
    modifier: Modifier = Modifier
) {
    val hostState = remember { SnackbarHostState() }
    if (failure != null) {
        val message = stringResource(R.string.save_failed)
        val currentOnShown by rememberUpdatedState(onShown)
        // Keyed by the failure, so one that replaces another is shown too.
        LaunchedEffect(failure) {
            try {
                hostState.showSnackbar(message)
            } finally {
                // Also once the screen is gone, such as when the scout leaves it while the
                // message shows, so the old message doesn't come back when they return.
                currentOnShown(failure)
            }
        }
    }
    SnackbarHost(hostState, modifier)
}
