package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.data.BackgroundTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Background time that counts what holds it, for a test to read. Work can begin and end on any
 * thread, so the counts are kept in MutableStateFlows, which are thread-safe in common code too.
 */
class FakeBackgroundTime : BackgroundTime {
    private val begun = MutableStateFlow(0)
    private val ended = MutableStateFlow(0)

    /** How many pieces of work have held it. */
    val begins: Int get() = begun.value

    /** How many pieces of work hold it now. */
    val holding: Int get() = begun.value - ended.value

    override suspend fun <T> whileRunning(block: suspend () -> T): T {
        begun.update { it + 1 }
        try {
            return block()
        } finally {
            ended.update { it + 1 }
        }
    }
}
