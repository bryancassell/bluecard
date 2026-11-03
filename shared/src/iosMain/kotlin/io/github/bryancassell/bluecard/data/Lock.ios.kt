package io.github.bryancassell.bluecard.data

import platform.Foundation.NSRecursiveLock

// Recursive, so the thread that holds it can take it again, as with Android's JVM monitor.
internal actual class Lock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(action: () -> T): T {
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
