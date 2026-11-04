package io.github.bryancassell.bluecard.data.progress

import androidx.room3.Room
import androidx.sqlite.SQLiteException
import kotlin.test.AfterTest
import okio.Path

/** [SetAsideDamagedDatabaseDriverTest] with iOS's database builder, on the iOS simulator. */
class IosSetAsideDamagedDatabaseDriverTest : SetAsideDamagedDatabaseDriverTest() {
    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(file.toString(), BlueCardDatabaseConstructor::initialize)

    override val helper = migrationTestHelper(file)

    @AfterTest
    fun closeConnections() = helper.finished()

    // iOS's error takes only a message, so sqliteException attaches the cause as suppressed.
    override fun causeOf(error: SQLiteException) = error.suppressedExceptions.singleOrNull()
}
