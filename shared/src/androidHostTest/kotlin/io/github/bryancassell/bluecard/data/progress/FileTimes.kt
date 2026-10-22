package io.github.bryancassell.bluecard.data.progress

import java.io.File
import org.junit.Assert.assertTrue

/**
 * Dates [older] two minutes ago and [newer] one minute ago, as files written at two launches
 * would be, so their times can't tie.
 */
internal fun dateInOrder(older: File, newer: File) {
    val now = System.currentTimeMillis()
    assertTrue(older.setLastModified(now - 120_000))
    assertTrue(newer.setLastModified(now - 60_000))
}
