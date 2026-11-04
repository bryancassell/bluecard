package io.github.bryancassell.bluecard.data.progress

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toLong
import okio.Path
import platform.posix.pthread_self

/** [RoomProgressRepositoryTest] with iOS's database builders, on the iOS simulator. */
class IosRoomProgressRepositoryTest : RoomProgressRepositoryTest() {
    override fun inMemoryDatabaseBuilder() =
        Room.inMemoryDatabaseBuilder(BlueCardDatabaseConstructor::initialize)

    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(file.toString(), BlueCardDatabaseConstructor::initialize)

    @OptIn(ExperimentalForeignApi::class)
    override fun currentThreadId() = pthread_self().toLong()
}
