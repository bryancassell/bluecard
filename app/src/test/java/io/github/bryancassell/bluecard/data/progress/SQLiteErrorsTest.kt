package io.github.bryancassell.bluecard.data.progress

import android.database.SQLException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs under Robolectric, since a driver's SQLiteException is Android's SQLException. */
@RunWith(AndroidJUnit4::class)
class SQLiteErrorsTest {
    @Test
    fun resultCode_isReadFromTheMessage() {
        val error = SQLException("Error code: 11, message: database disk image is malformed")

        assertEquals(SQLITE_CORRUPT, error.resultCode)
    }

    @Test
    fun resultCode_ofAnExtendedCode_isItsPrimaryCode() {
        // SQLITE_CONSTRAINT_TRIGGER, as a trigger's RAISE(ABORT) gives.
        val error = SQLException("Error code: 1811, message: Test failure")

        assertEquals(SQLITE_CONSTRAINT, error.resultCode)
    }

    @Test
    fun resultCode_whenTheMessageHasNone_isNull() {
        assertNull(SQLException("Foreign key violation(s) detected in 'tracker_entry'.").resultCode)
        assertNull(SQLException().resultCode)
    }

    @Test
    fun isDamage_forACorruptDatabaseOrAFileThatIsntOne() {
        assertTrue(SQLException("Error code: 11").isDamage())
        // SQLITE_CORRUPT_INDEX.
        assertTrue(SQLException("Error code: 779").isDamage())
        assertTrue(SQLException("Error code: 26, message: file is not a database").isDamage())
        assertFalse(SQLException("Error code: 13, message: database or disk is full").isDamage())
    }
}
