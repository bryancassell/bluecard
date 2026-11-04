package io.github.bryancassell.bluecard.data.progress

import androidx.room3.Room
import platform.Foundation.NSThread

/** [RoomProgressRepositoryTest] with iOS's in-memory database builder, on the iOS simulator. */
class IosRoomProgressRepositoryTest : RoomProgressRepositoryTest() {
    override fun inMemoryDatabaseBuilder() =
        Room.inMemoryDatabaseBuilder(BlueCardDatabaseConstructor::initialize)

    // Such as "<NSThread: 0x6000017d4a00>{number = 5, name = (null)}", whose address no other
    // running thread has.
    override fun currentThread() = NSThread.currentThread.toString()
}
