package io.github.bryancassell.bluecard.di

/**
 * Where the app writes the failures it catches, such as stored data that couldn't be read. It
 * reports them nowhere else (ARCHITECTURE.md, Load and save failures), so this is the only way
 * to tell what failed and why. An interface, so each platform writes them its own way, and tests
 * can read them back: Android's `Log` throws in plain local tests. It's called from any thread,
 * such as the IO dispatcher's, so an implementation must be thread-safe. It must not throw either,
 * since callers write to it while handling a failure.
 */
interface WarningLog {
    /** Writes [message], and [error] if there is one, under [tag]. */
    fun warn(tag: String, message: String, error: Throwable? = null)
}
