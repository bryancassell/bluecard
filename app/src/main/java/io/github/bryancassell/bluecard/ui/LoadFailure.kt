package io.github.bryancassell.bluecard.ui

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "LoadFailure"

/**
 * Logs an [IOException], which repositories throw when stored data can't be read, and emits
 * [failed] in its place. Any other exception is a bug, so it still crashes the app and
 * reaches crash reports instead of hiding behind the load-failed message (see ARCHITECTURE.md,
 * Load and save failures).
 *
 * To keep it simple, the message (`R.string.load_failed`) has no "Try again" button: it asks the
 * scout to close and reopen the app. A screen loads again only when its ViewModel is created, or
 * when it's shown after being hidden for more than 5 seconds (`WhileSubscribed(5_000)`). Home and
 * the navigation root keep their ViewModels for as long as the activity lives, and on Android 12
 * and higher Back on Home moves the app to the background instead of finishing the activity
 * (https://developer.android.com/about/versions/12/behavior-changes-all), so reopening the app
 * within 5 seconds shows the message again. A failure after a screen has loaded also stays until
 * the screen loads again. A "Try again" button would fix both.
 */
fun <T> Flow<T>.catchLoadFailure(failed: T): Flow<T> = catch {
    if (it !is IOException) throw it
    // The app reports caught exceptions nowhere else, so logcat and bug reports are the
    // only way to tell which data failed and why.
    Log.w(TAG, "Couldn't load stored data", it)
    emit(failed)
}
