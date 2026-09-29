package io.github.bryancassell.bluecard.data.progress

import android.database.sqlite.SQLiteBindOrColumnIndexOutOfRangeException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatatypeMismatchException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteMisuseException
import androidx.room.withTransaction
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * [ProgressRepository] backed by Room.
 *
 * Functions are written as expression bodies (`= dao...`): when a call really suspends,
 * JaCoCo never sees a separate closing brace run, and would count it as untested.
 */
class RoomProgressRepository @Inject constructor(private val database: BlueCardDatabase) :
    ProgressRepository {
    private val dao = database.progressDao()

    override fun observeAllProgress(): Flow<List<BadgeProgressDetails>> =
        dao.observeAll().readFailuresAsIOException()

    override fun observeProgress(badgeId: String): Flow<BadgeProgressDetails?> =
        dao.observe(badgeId).readFailuresAsIOException()

    override suspend fun startBadge(
        badgeId: String,
        requirementsVersion: LocalDate,
        startedDate: LocalDate
    ) = writing { dao.insertBadge(BadgeProgress(badgeId, requirementsVersion, startedDate)) }

    override suspend fun setCounselor(badgeId: String, counselor: Counselor?) = ifStarted(badgeId) {
        val stored = counselor?.normalized()
        dao.updateCounselor(badgeId, stored?.name, stored?.phone, stored?.email)
    }

    override suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate?) =
        ifStarted(badgeId) { dao.updateCompletedOnPriorDate(badgeId, date) }

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?,
        start: BadgeStart?
    ) = ifStarted(badgeId, start) {
        dao.updateRequirement(badgeId, number) {
            it.copy(completed = true, completedDate = completedDate)
        }
    }

    override suspend fun markRequirementNotCompleted(badgeId: String, number: String) =
        ifStarted(badgeId) {
            dao.updateRequirement(badgeId, number) {
                it.copy(completed = false, completedDate = null)
            }
        }

    override suspend fun setRequirementComment(
        badgeId: String,
        number: String,
        comment: String?,
        start: BadgeStart?
    ) = ifStarted(badgeId, start) {
        dao.updateRequirement(badgeId, number) { it.copy(comment = comment?.ifBlank { null }) }
    }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        values: Map<String, String>
    ): Long = ifStarted(badgeId) {
        dao.insertTrackerEntry(
            TrackerEntry(badgeId = badgeId, requirementNumber = number, values = values)
        )
    }

    override suspend fun updateTrackerEntry(id: Long, values: Map<String, String>) =
        writing { dao.updateTrackerEntry(id, values) }

    override suspend fun deleteTrackerEntry(id: Long) = writing { dao.deleteTrackerEntry(id) }

    override suspend fun clearRequirement(badgeId: String, number: String) =
        writing { dao.deleteRequirement(badgeId, number) }

    override suspend fun clearBadge(badgeId: String) = writing { dao.deleteBadge(badgeId) }

    override suspend fun clearAll() = writing { dao.deleteAll() }

    /**
     * Reports a database that can't be read, such as one that can't be opened, as the
     * [IOException] that [ProgressRepository] documents. Android's SQLite reports those as
     * [SQLiteException]s ([isStorageFailure]). Other exceptions are bugs and pass through: a
     * missing migration, and the parent class `android.database.SQLException`, which Room's
     * SQLite adapter throws only for misuse such as reading a closed statement. If Room is ever
     * given a `SQLiteDriver`, every SQLite error arrives as that parent class, so this check
     * needs revisiting.
     */
    private fun <T> Flow<T>.readFailuresAsIOException(): Flow<T> = catch {
        throw if (it is SQLiteException && it.isStorageFailure()) IOException(it) else it
    }

    /**
     * Runs [write], reporting a database that can't be written, such as one that can't be
     * opened or a full disk, as an [IOException], in the same way as
     * [readFailuresAsIOException].
     */
    private suspend fun <T> writing(write: suspend () -> T): T = try {
        write()
    } catch (e: SQLiteException) {
        throw if (e.isStorageFailure()) IOException(e) else e
    }

    /**
     * Whether the database couldn't be read or written, such as a full disk or a file that
     * can't be opened, rather than the app using it wrongly. SQLite's result codes describe a
     * constraint violation, misuse of its interface, an out-of-range parameter or column number
     * and a datatype mismatch as mistakes in the app's code:
     * https://www.sqlite.org/rescode.html
     */
    private fun SQLiteException.isStorageFailure() = this !is SQLiteConstraintException &&
        this !is SQLiteMisuseException &&
        this !is SQLiteBindOrColumnIndexOutOfRangeException &&
        this !is SQLiteDatatypeMismatchException

    /**
     * Runs [action] if the badge is started, or once [start] has started it, checking and
     * writing in one transaction.
     */
    private suspend fun <T> ifStarted(
        badgeId: String,
        start: BadgeStart? = null,
        action: suspend () -> T
    ): T = writing {
        database.withTransaction {
            if (!dao.isStarted(badgeId)) {
                if (start == null) throw notStartedError(badgeId)
                dao.insertBadge(
                    BadgeProgress(badgeId, start.requirementsVersion, start.startedDate)
                )
            }
            action()
        }
    }
}
