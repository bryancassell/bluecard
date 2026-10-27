package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteException

// Android's driver error is android.database.SQLException, which takes a cause.
internal actual fun sqliteException(message: String, cause: Throwable): SQLiteException =
    SQLiteException(message, cause)
