package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.di.WarningLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A log that keeps what's written to it, for a test to read back. Warnings can come from any
 * thread, such as the IO dispatcher's, so they're kept in a MutableStateFlow, which is
 * thread-safe in common code too, unlike `synchronized`.
 */
class FakeWarningLog : WarningLog {
    /** One warning, as it was written. */
    data class Warning(val tag: String, val message: String, val error: Throwable?)

    private val written = MutableStateFlow(emptyList<Warning>())

    /** What's been written so far, oldest first. */
    val warnings: List<Warning> get() = written.value

    override fun warn(tag: String, message: String, error: Throwable?) {
        written.update { it + Warning(tag, message, error) }
    }
}
