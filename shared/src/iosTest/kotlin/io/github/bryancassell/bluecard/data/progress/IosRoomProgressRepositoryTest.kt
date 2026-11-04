package io.github.bryancassell.bluecard.data.progress

import androidx.room3.Room
import okio.Path
import platform.Foundation.NSThread

/** [RoomProgressRepositoryTest] with iOS's database builders, on the iOS simulator. */
class IosRoomProgressRepositoryTest : RoomProgressRepositoryTest() {
    override fun inMemoryDatabaseBuilder() =
        Room.inMemoryDatabaseBuilder(BlueCardDatabaseConstructor::initialize)

    override fun databaseBuilder(file: Path) =
        Room.databaseBuilder(file.toString(), BlueCardDatabaseConstructor::initialize)

    // Such as "<NSThread: 0x6000017d4a00>{number = 5, name = (null)}", whose address no other
    // running thread has.
    override fun currentThread() = NSThread.currentThread.toString()
}
