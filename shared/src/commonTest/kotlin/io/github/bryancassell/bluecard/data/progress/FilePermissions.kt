package io.github.bryancassell.bluecard.data.progress

import okio.Path

/**
 * Lets the owner write in [path], or stops them, returning whether it could. Stopping them keeps
 * files in a folder from being made, moved or deleted, as long as the tests don't run as root,
 * which CI doesn't. Common code has no way to, so each platform's tests give their own.
 */
expect fun setWritable(path: Path, writable: Boolean): Boolean

/** Runs [action] while the owner can't write in [path]. Inline, so [action] can suspend. */
internal inline fun <T> whileReadOnly(path: Path, action: () -> T): T {
    check(setWritable(path, false)) { "Couldn't make $path read-only" }
    try {
        return action()
    } finally {
        // Unchecked, so it can't hide the test's own failure, such as a file that's gone. A
        // folder left read-only fails the test's cleanup, which deletes it.
        setWritable(path, true)
    }
}
