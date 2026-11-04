package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * [SetAsideDamagedDatabaseDriverTest] under Robolectric: Android's database builder and helper
 * need it, and the local tests' android.jar drops the message of SQLite's errors, which holds the
 * result code.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSetAsideDamagedDatabaseDriverTest : SetAsideDamagedDatabaseDriverTest() {
    // Android's error takes a cause.
    override fun causeOf(error: SQLiteException) = error.cause
}
