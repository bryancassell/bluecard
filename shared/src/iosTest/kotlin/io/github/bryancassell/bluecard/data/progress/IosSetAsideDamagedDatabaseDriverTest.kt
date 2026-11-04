package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteException

/** [SetAsideDamagedDatabaseDriverTest] on the iOS simulator. */
class IosSetAsideDamagedDatabaseDriverTest : SetAsideDamagedDatabaseDriverTest() {
    // iOS's error takes only a message, so sqliteException attaches the cause as suppressed.
    override fun causeOf(error: SQLiteException) = error.suppressedExceptions.singleOrNull()
}
