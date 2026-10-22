package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.ApplicationScope
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import okio.IOException

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
 * [block] starts in the caller's thread, so blocks start in the order they're run, and runs on
 * the caller's dispatcher, so it must be main-safe, as any suspend function is. On this scope's
 * dispatcher, IO in the app, its context would name IO while it ran in the caller's thread, so a
 * `withContext` to IO would see no change and stay there: FileDamagedProgressRepository's
 * dismissNotice deleted a file on the main thread, and a write ran its SQL there, since Room 3
 * runs queries on Dispatchers.IO too (#361). A block outlives its caller only while the
 * caller's dispatcher runs, as the main thread's does for the app's life.
 */
suspend fun <T> CoroutineScope.runOutlivingCaller(block: suspend () -> T): T {
    val result = CompletableDeferred<T>()
    // currentCoroutineContext(), since coroutineContext here is this scope's.
    val callerDispatcher =
        currentCoroutineContext()[ContinuationInterceptor] ?: EmptyCoroutineContext
    launch(callerDispatcher, start = CoroutineStart.UNDISPATCHED) {
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
            if (e !is IOException) throw e
        }
    }
    return result.await()
}
