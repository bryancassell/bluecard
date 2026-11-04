package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.Path
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * [SetAsideDamagedDatabaseDriverTest] with Android's database builder, under Robolectric: the
 * helper needs it, and the local tests' android.jar drops the message of SQLite's errors, which
 * holds the result code.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSetAsideDamagedDatabaseDriverTest : SetAsideDamagedDatabaseDriverTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(context, file.toString(), BlueCardDatabaseConstructor::initialize)

    // Public, as JUnit needs a rule to be.
    @get:Rule
    public override val helper = migrationTestHelper(file)

    // Android's error takes a cause.
    override fun causeOf(error: SQLiteException) = error.cause
}
