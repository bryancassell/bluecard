package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Runs the repository contract against Room with an in-memory database. Robolectric
 * provides the Context that Room needs on Android (see ARCHITECTURE.md, Testing approach).
 */
@RunWith(AndroidJUnit4::class)
class RoomProgressRepositoryTest : ProgressRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<BlueCardDatabase>()

    private fun open(builder: RoomDatabase.Builder<BlueCardDatabase>) =
        builder.build().also { databases += it }

    override val repository = RoomProgressRepository(
        open(Room.inMemoryDatabaseBuilder(context, BlueCardDatabase::class.java))
    )

    // A folder where the database file should be, so SQLite can't open it.
    override fun unreadableRepository() = RoomProgressRepository(
        open(
            Room.databaseBuilder(
                context,
                BlueCardDatabase::class.java,
                folder.newFolder(BlueCardDatabase.NAME).absolutePath
            )
        )
    )

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }
}
