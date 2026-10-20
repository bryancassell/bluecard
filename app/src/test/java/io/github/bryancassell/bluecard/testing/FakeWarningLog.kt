package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.di.WarningLog

/**
 * A log that keeps what's written to it, for a test to read back. Warnings can come from any
 * thread, such as the IO dispatcher's, so the list is read and written under a lock.
 */
class FakeWarningLog : WarningLog {
    /** One warning, as it was written. */
    data class Warning(val tag: String, val message: String, val error: Throwable?)

    private val written = mutableListOf<Warning>()

    /** What's been written so far, oldest first. */
    val warnings: List<Warning> get() = synchronized(written) { written.toList() }

    override fun warn(tag: String, message: String, error: Throwable?) {
        synchronized(written) { written += Warning(tag, message, error) }
    }
}
