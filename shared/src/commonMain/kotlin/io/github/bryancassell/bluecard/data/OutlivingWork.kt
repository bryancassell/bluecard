package io.github.bryancassell.bluecard.data

import io.github.bryancassell.bluecard.di.ApplicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.IOException

/**
 * Runs work that must outlive the screen that starts it, such as a save, in [scope], which lives
 * as long as the app ([ApplicationScope]). That's how the data layer guide has an operation live
 * longer than the screen:
 * https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
 *
 * The work also holds [backgroundTime] while it runs, so it can finish if the scout leaves the
 * app. iOS suspends an app a few seconds after it leaves the screen, which would pause a save
 * there, and lose it if iOS then ends the app (#354).
 */
class OutlivingWork(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val backgroundTime: BackgroundTime
) {
    /**
     * Runs [block] and waits for it. If the caller is cancelled, only the wait is: [block] still
     * finishes, so a save does when the scout leaves the screen that made it.
     *
     * The caller gets [block]'s result or exception. An [IOException], which repositories report
     * data that can't be read or saved as, ends there. Any other exception is a bug: it also
     * crashes the app through [scope], even if the caller is gone.
     *
     * [block] starts in the caller's thread, so blocks start in the order they're run. After that
     * it isn't confined to a dispatcher (Dispatchers.Unconfined): it goes on in whichever thread
     * resumes it, which can be one Room or DataStore runs their own work on. So it must be
     * main-safe, as any suspend function is, switching with `withContext` for blocking work. Its
     * context never names a dispatcher it isn't running on, so a `withContext` to IO always
     * leaves the caller's thread (#361). The Kotlin docs keep Unconfined for cases like this,
     * where something must happen right away. Two other dispatchers were tried:
     * - The scope's, IO in the app. While the block ran in the caller's thread, its context named
     *   IO, so `withContext(IO)`, and Room's own switch to Dispatchers.IO, saw no change and ran
     *   there: withContext's same-dispatcher shortcut in kotlinx.coroutines 1.11.0, and Room's
     *   default query context in room3-runtime 3.0.3.
     * - The caller's. That did the same for a caller whose context names a dispatcher it isn't
     *   running on, or none, and each write waited for that dispatcher, usually Main, to hand on
     *   the write lock.
     */
    suspend fun <T> run(block: suspend () -> T): T {
        val result = CompletableDeferred<T>()
        // Not injected, unlike the app's other dispatchers: any other in its place would name a
        // dispatcher the block may not be running on.
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try {
                result.complete(backgroundTime.whileRunning(block))
            } catch (e: Throwable) {
                result.completeExceptionally(e)
                if (e !is IOException) throw e
            }
        }
        return result.await()
    }
}

/**
 * Keeps the app running while [OutlivingWork] finishes, after the scout leaves the app. Each
 * platform has its own, since only iOS needs to ask for the time.
 */
interface BackgroundTime {
    /** Runs [block], keeping the app running until it returns or throws. */
    suspend fun <T> whileRunning(block: suspend () -> T): T
}
