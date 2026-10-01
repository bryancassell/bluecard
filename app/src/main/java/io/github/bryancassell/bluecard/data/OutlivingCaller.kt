package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.ApplicationScope
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Runs [block] in this scope, which lives longer than its caller, such as [ApplicationScope],
 * and waits for it. If the caller is cancelled, only the wait is: [block] still finishes, so a
 * save does when the scout leaves the screen that made it. That's how the data layer guide has
 * an operation live longer than the screen:
 * https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
 *
 * The caller gets [block]'s result or exception. An [IOException], which repositories report
 * data that can't be read or saved as, ends there. Any other exception is a bug: it also
 * crashes the app through this scope, even if the caller is gone.
 *
 * [block] starts in the caller's thread, so blocks start in the order they're run.
 */
suspend fun <T> CoroutineScope.runOutlivingCaller(block: suspend () -> T): T {
    val result = CompletableDeferred<T>()
    launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
            if (e !is IOException) throw e
        }
    }
    return result.await()
}
