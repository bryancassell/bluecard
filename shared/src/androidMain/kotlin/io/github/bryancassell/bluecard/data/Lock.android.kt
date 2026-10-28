package io.github.bryancassell.bluecard.data

internal actual class Lock actual constructor() {
    actual fun <T> withLock(action: () -> T): T = synchronized(this, action)
}
