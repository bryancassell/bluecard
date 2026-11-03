package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteException

// iOS's driver error is final and takes only a message (androidx.sqlite 2.7.1), so the cause is
// attached as suppressed, which its stack trace still prints.
internal actual fun sqliteException(message: String, cause: Throwable): SQLiteException =
    SQLiteException(message).apply { addSuppressed(cause) }
