package io.github.bryancassell.bluecard.data.progress

import kotlin.time.Clock
import okio.Path

/**
 * Sets when [file] was last changed, in milliseconds since 1970, failing if it can't. Common
 * code has no way to, so each platform's tests give their own.
 */
expect fun setLastModified(file: Path, millis: Long)

/**
 * Dates [older] two minutes ago and [newer] one minute ago, as files written at two launches
 * would be, so their times can't tie.
 */
internal fun dateInOrder(older: Path, newer: Path) {
    val now = Clock.System.now().toEpochMilliseconds()
    setLastModified(older, now - 120_000)
    setLastModified(newer, now - 60_000)
}
