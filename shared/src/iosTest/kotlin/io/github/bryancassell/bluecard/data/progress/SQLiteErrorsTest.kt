package io.github.bryancassell.bluecard.data.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLiteErrorsTest {
    @Test
    fun sqliteException_keepsTheMessage_whoseResultCodeIsRead() {
        val error = sqliteException(
            "Error code: $SQLITE_CORRUPT, message: unreadable value",
            IllegalArgumentException()
        )

        assertEquals("Error code: $SQLITE_CORRUPT, message: unreadable value", error.message)
        assertTrue(error.isDamage())
    }

    @Test
    fun sqliteException_keepsTheCause() {
        val cause = IllegalArgumentException("Unreadable date: 2026-13-45")

        val error = sqliteException("Error code: $SQLITE_CORRUPT, message: unreadable value", cause)

        assertEquals(listOf<Throwable>(cause), error.suppressedExceptions)
    }
}
