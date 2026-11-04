package io.github.bryancassell.bluecard.data.progress

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okio.Path
import org.junit.runner.RunWith

/**
 * [RoomProgressRepositoryTest] with Android's database builders, under Robolectric. The Room
 * testing guide recommends plain JVM tests instead. Room 3 can open the database without
 * Robolectric, but on Android its errors are android.database.SQLException, and the local tests'
 * android.jar drops their message, which holds the result code the repository sorts them by.
 */
@RunWith(AndroidJUnit4::class)
class AndroidRoomProgressRepositoryTest : RoomProgressRepositoryTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    override fun inMemoryDatabaseBuilder() =
        Room.inMemoryDatabaseBuilder(context, BlueCardDatabaseConstructor::initialize)

    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(context, file.toString(), BlueCardDatabaseConstructor::initialize)

    override fun currentThread() = Thread.currentThread().let { "${it.name} #${it.threadId()}" }
}
