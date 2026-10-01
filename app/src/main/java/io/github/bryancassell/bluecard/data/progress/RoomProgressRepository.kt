package io.github.bryancassell.bluecard.data.progress

import android.database.sqlite.SQLiteBindOrColumnIndexOutOfRangeException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatatypeMismatchException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteMisuseException
import androidx.room.withTransaction
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ProgressRepository] backed by Room.
 *
 * Writes run in [externalScope], which lives as long as the app, so one finishes even if its
 * caller is cancelled, such as when the scout leaves the screen that made it. That's how the
 * data layer guide has an operation live longer than the screen:
 * https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
 *
 * Functions are written as expression bodies (`= dao...`): when a call really suspends,
 * JaCoCo never sees a separate closing brace run, and would count it as untested.
 */
class RoomProgressRepository @Inject constructor(
    private val database: BlueCardDatabase,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ProgressRepository {
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

    override suspend fun setCounselor(badgeId: String, counselor: Counselor?, start: BadgeStart) =
        ifStarted(badgeId, start) {
            val stored = counselor?.normalized()
            dao.updateCounselor(badgeId, stored?.name, stored?.phone, stored?.email)
        }

    override suspend fun setCompletedOnPriorDate(badgeId: String, date: LocalDate?) =
        ifStarted(badgeId) { dao.updateCompletedOnPriorDate(badgeId, date) }

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?,
        start: BadgeStart
    ): Unit = ifStarted(badgeId, start) {
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

    override suspend fun setRequirementCompletedDate(
        badgeId: String,
        number: String,
        date: LocalDate?
    ) = writing { dao.updateCompletedDate(badgeId, number, date) }

    override suspend fun setRequirementComment(
        badgeId: String,
        number: String,
        comment: String?,
        start: BadgeStart
    ): Unit = ifStarted(badgeId, start) {
        dao.updateRequirement(badgeId, number) { it.copy(comment = normalizedText(comment)) }
    }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        rowNumber: Int?,
        values: Map<String, String>,
        addedDate: LocalDate,
        start: BadgeStart,
        id: Long?
    ): Long = ifStarted(badgeId, start) {
        dao.addTrackerEntry(
            TrackerEntry(
                id = id ?: 0,
                badgeId = badgeId,
                requirementNumber = number,
                rowNumber = rowNumber,
                values = normalizedTrackerValues(values),
                addedDate = addedDate
            )
        )
    }

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

    /** Lets writes in one at a time, in the order they were made (it's first come, first served). */
    private val writeOrder = Mutex()

    /**
     * Runs [write] in [externalScope] and waits for it. If the caller is cancelled, only the
     * wait is: the write still finishes ([runOutlivingCaller]). Reports a database that can't be
     * written, such as one that can't be opened or a full disk, as an [IOException], in the same
     * way as [readFailuresAsIOException]. Any other exception is a bug: the caller gets it too,
     * but it crashes the app through [externalScope] even if the caller is gone.
     */
    // Started in the caller's thread, so writes queue for writeOrder in the order they're made.
    private suspend fun <T> writing(write: suspend () -> T): T = externalScope.runOutlivingCaller {
        try {
            writeOrder.withLock { write() }
        } catch (e: SQLiteException) {
            throw if (e.isStorageFailure()) IOException(e) else e
        }
    }

    /**
     * Whether the database couldn't be read or written, such as a full disk or a file that
     * can't be opened, rather than the app using it wrongly. SQLite says misuse of its
     * interface means the app "is incorrectly coded" (https://www.sqlite.org/rescode.html).
     * This app also counts a constraint violation, an out-of-range parameter or column number
     * and a datatype mismatch as bugs: each write checks what it needs first, such as that the
     * badge is started, so only a mistake in the code can cause one.
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
                dao.insertBadge(start.progress(badgeId))
            }
            action()
        }
    }
}
