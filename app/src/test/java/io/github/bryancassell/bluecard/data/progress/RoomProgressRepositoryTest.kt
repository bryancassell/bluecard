package io.github.bryancassell.bluecard.data.progress

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.runner.RunWith

/**
 * Runs the repository contract against Room with an in-memory database. Robolectric
 * provides the Context that Room needs on Android (see ARCHITECTURE.md, Testing approach).
 */
@RunWith(AndroidJUnit4::class)
class RoomProgressRepositoryTest : ProgressRepositoryContract() {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        BlueCardDatabase::class.java
    ).build()

    override val repository = RoomProgressRepository(database)

    @After
    fun closeDatabase() {
        database.close()
    }
}
