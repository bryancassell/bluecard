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
import kotlinx.coroutines.launch

private const val TAG = "SaveFailure"

/**
 * Launches [save]. If it throws an [IOException], which repositories throw when data can't be
 * saved, logs it and sets [failed], which the screen shows with [SaveFailedSnackbarHost]. Any
 * other exception is a bug, so it still crashes the app, as in [catchLoadFailure].
 */
fun CoroutineScope.launchSave(failed: MutableStateFlow<Boolean>, save: suspend () -> Unit): Job =
    launch {
        try {
            save()
        } catch (e: IOException) {
            // The app reports caught exceptions nowhere else, so logcat and bug reports are
            // the only way to tell what failed and why.
            Log.w(TAG, "Couldn't save", e)
            failed.value = true
        }
    }

/**
 * Shows a snackbar saying progress couldn't be saved while [saveFailed], then calls [onShown]
 * once it's gone, so the ViewModel can clear it. That's how the UI layer guide has the UI
 * show a message from UI state:
 * https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events
 */
@Composable
fun SaveFailedSnackbarHost(
    saveFailed: Boolean,
    onShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hostState = remember { SnackbarHostState() }
    if (saveFailed) {
        val message = stringResource(R.string.save_failed)
        val currentOnShown by rememberUpdatedState(onShown)
        LaunchedEffect(hostState) {
            hostState.showSnackbar(message)
            currentOnShown()
        }
    }
    SnackbarHost(hostState, modifier)
}
