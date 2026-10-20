package io.github.bryancassell.bluecard.data.progress

import androidx.sqlite.SQLiteException

/**
 * The primary result code of an error from a SQLite driver (https://www.sqlite.org/rescode.html),
 * or null if its message has none. Every driver error is one class, with the code only in its
 * message, as in "Error code: 11, message: database disk image is malformed". Since
 * androidx.sqlite 2.6.2 that's an extended code, whose low byte is the primary one, such as 1811
 * (SQLITE_CONSTRAINT_TRIGGER) for 19 (SQLITE_CONSTRAINT).
 */
val SQLiteException.resultCode: Int?
    get() = message?.let { RESULT_CODE.find(it) }?.groupValues?.get(1)?.toInt()?.and(0xFF)

private val RESULT_CODE = Regex("""^Error code: (\d+)""")

/** Whether SQLite found the database damaged. */
fun SQLiteException.isDamage() = resultCode.let { it == SQLITE_CORRUPT || it == SQLITE_NOTADB }

// The primary result codes this app checks for.
const val SQLITE_CORRUPT = 11
const val SQLITE_CONSTRAINT = 19
const val SQLITE_MISMATCH = 20
const val SQLITE_MISUSE = 21
const val SQLITE_RANGE = 25
const val SQLITE_NOTADB = 26
