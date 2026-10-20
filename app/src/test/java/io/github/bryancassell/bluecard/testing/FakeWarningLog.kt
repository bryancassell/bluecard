package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.di.WarningLog

/** A log that keeps what's written to it, for a test to read back. */
class FakeWarningLog : WarningLog {
    /** One warning, as it was written. */
    data class Warning(val tag: String, val message: String, val error: Throwable?)

    private val written = mutableListOf<Warning>()

    /** What's been written, oldest first. */
    val warnings: List<Warning> get() = written

    override fun warn(tag: String, message: String, error: Throwable?) {
        written += Warning(tag, message, error)
    }
}
