package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.Path
import org.junit.Rule
import org.junit.runner.RunWith

/** [MigrationTest] with Android's database builder, under Robolectric, which the helper needs. */
@RunWith(AndroidJUnit4::class)
class AndroidMigrationTest : MigrationTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(context, file.toString(), BlueCardDatabaseConstructor::initialize)

    // Public, as JUnit needs a rule to be.
    @get:Rule
    public override val helper = migrationTestHelper(file)
}
