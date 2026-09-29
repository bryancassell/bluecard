package io.github.bryancassell.bluecard.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bryancassell.bluecard.R
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "LoadFailure"

/**
 * Logs an [IOException], which repositories throw when stored data can't be read, and emits
 * [failed] in its place. Any other exception is a bug, so it still crashes the app and
 * reaches crash reports instead of hiding behind [LoadFailedMessage] (see ARCHITECTURE.md,
 * UI layer).
 */
fun <T> Flow<T>.catchLoadFailure(failed: T): Flow<T> = catch {
    if (it !is IOException) throw it
    // The app reports caught exceptions nowhere else, so logcat and bug reports are the
    // only way to tell which data failed and why.
    Log.w(TAG, "Couldn't load stored data", it)
    emit(failed)
}

/** Shown in place of a screen's content when its data couldn't be loaded. */
@Composable
fun LoadFailedMessage(modifier: Modifier = Modifier) {
    ScreenMessage(stringResource(R.string.load_failed), modifier)
}
