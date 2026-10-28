package io.github.bryancassell.bluecard.data

/**
 * Lets one thread at a time run what it guards, as the JVM's `synchronized` does, which common
 * code can't use. A thread that holds it can take it again, as with `synchronized`, so code that
 * works on Android can't deadlock on another platform alone. Each platform has its own, since
 * atomicfu's, the one common lock, is explicitly experimental (#346). `LockTest` checks
 * Android's, with JVM threads.
 */
internal expect class Lock() {
    /** Runs [action] once no other thread holds this lock, holding it until [action] returns. */
    fun <T> withLock(action: () -> T): T
}
