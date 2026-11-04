package io.github.bryancassell.bluecard.data.progress

import androidx.room3.Room
import kotlin.test.AfterTest
import okio.Path

/** [MigrationTest] with iOS's database builder, on the iOS simulator. */
class IosMigrationTest : MigrationTest() {
    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(file.toString(), BlueCardDatabaseConstructor::initialize)

    override val helper = migrationTestHelper(file)

    @AfterTest
    fun closeConnections() = helper.finished()
}
