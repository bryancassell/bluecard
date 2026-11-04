package io.github.bryancassell.bluecard.data.progress

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import io.github.bryancassell.bluecard.data.OutlivingWork
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import okio.IOException

/**
 * [ProgressRepository] backed by Room.
 *
 * Writes run as [outlivingWork], so one finishes even if its caller is cancelled, such as when
 * the scout leaves the screen that made it.
 *
 * Functions are written as expression bodies (`= dao...`): when a call really suspends,
 * JaCoCo never sees a separate closing brace run, and would count it as untested.
 */
class RoomProgressRepository(
    private val database: BlueCardDatabase,
    private val outlivingWork: OutlivingWork
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
        afterStarting(badgeId, start) {
            val stored = counselor?.normalized()
            dao.updateCounselor(badgeId, stored?.name, stored?.phone, stored?.email)
        }

    override suspend fun setCompletedOnPriorDate(
        badgeId: String,
        date: LocalDate,
        start: BadgeStart
    ) = afterStarting(badgeId, start) { dao.updateCompletedOnPriorDate(badgeId, date) }

    // Updates nothing for a badge that isn't started.
    override suspend fun removeCompletedOnPriorDate(badgeId: String) =
        writing { dao.updateCompletedOnPriorDate(badgeId, null) }

    override suspend fun markRequirementCompleted(
        badgeId: String,
        number: String,
        completedDate: LocalDate?,
        start: BadgeStart
    ): Unit = afterStarting(badgeId, start) {
        dao.updateRequirement(badgeId, number) {
            it.copy(completed = true, completedDate = completedDate)
        }
    }

    override suspend fun markRequirementNotCompleted(badgeId: String, number: String) =
        ifStarted(badgeId, notStarted = null) {
            dao.updateRequirement(badgeId, number) {
                it.copy(completed = false, completedDate = null)
            }
        }

    override suspend fun setRequirementCompletedDate(
        badgeId: String,
        number: String,
        date: LocalDate?
    ) = writing { dao.updateCompletedDate(badgeId, number, date) }

    override suspend fun setCompletedFromRowsDate(
        badgeId: String,
        number: String,
        rowCount: Int,
        date: LocalDate?
    ) = writing { dao.updateCompletedFromRowsDate(badgeId, number, rowCount, date) }

    override suspend fun setRequirementSignOffAndComment(
        badgeId: String,
        number: String,
        signedOffBy: String?,
        comment: String?,
        start: BadgeStart
    ): Unit = afterStarting(badgeId, start) {
        dao.updateRequirement(badgeId, number) {
            it.copy(signedOffBy = normalizedText(signedOffBy), comment = normalizedText(comment))
        }
    }

    override suspend fun addTrackerEntry(
        badgeId: String,
        number: String,
        rowNumber: Int?,
        values: Map<String, String>,
        addedDate: LocalDate,
        start: BadgeStart,
        id: Long?
    ): Long = afterStarting(badgeId, start) {
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

    override suspend fun clearRequirements(badgeId: String, numbers: Collection<String>) =
        writing { dao.deleteRequirements(badgeId, numbers) }

    override suspend fun clearBadge(badgeId: String) = writing { dao.deleteBadge(badgeId) }

    override suspend fun clearAll() = writing { dao.deleteAll() }

    override suspend fun replaceAll(progress: List<BadgeProgressDetails>) =
        writing { dao.replaceAll(progress) }

    override suspend fun merge(progress: List<BadgeProgressDetails>, replacing: Set<String>) =
        writing { dao.merge(progress, replacing) }

    /**
     * Reports a database that can't be read, such as one that can't be opened, as the
     * [IOException] that [ProgressRepository] documents ([isStorageFailure]). Other exceptions
     * are bugs and pass through, such as a missing migration.
     */
    private fun <T> Flow<T>.readFailuresAsIOException(): Flow<T> = catch {
        throw if (it.isStorageFailure()) IOException(it.toString(), it) else it
    }

    /**
     * Runs [write] as [outlivingWork] and waits for it. If the caller is cancelled, only the
     * wait is: the write still finishes ([OutlivingWork.run]). Reports a database that can't be
     * written, such as one that can't be opened or a full disk, as an [IOException], in the same
     * way as [readFailuresAsIOException]. Any other exception is a bug: the caller gets it too,
     * but it crashes the app through [outlivingWork] even if the caller is gone.
     */
    // Started in the caller's thread, so writes queue for the database's writeOrder in the order
    // they're made.
    private suspend fun <T> writing(write: suspend () -> T): T = outlivingWork.run {
        try {
            database.writeOrder.withLock { write() }
        } catch (e: Exception) {
            throw if (e.isStorageFailure()) IOException(e.toString(), e) else e
        }
    }

    /**
     * Whether the database couldn't be read or written, such as a full disk or a file that
     * can't be opened, rather than the app using it wrongly. SQLite says misuse of its
     * interface means the app "is incorrectly coded" (https://www.sqlite.org/rescode.html).
     * This app also counts a constraint violation, an out-of-range parameter or column number
     * and a datatype mismatch as bugs: each write checks what it needs first, such as that the
     * badge is started, so only a mistake in the code can cause one. So is an error with no
     * result code, which comes from Room rather than SQLite, such as a failed foreign key check.
     *
     * Damage crashes the app too, and on Android the next launch sets the database aside
     * ([SetAsideDamagedDatabaseDriver]). Reporting it as a load or save failure would leave the
     * scout retrying on a database that stays damaged until the app restarts.
     *
     * Before Room first opens the database, it locks a file beside it, and reports a lock file
     * it can't create or lock as an IllegalStateException ("Unable to open database …") caused
     * by its own, so that's a storage failure too. On Android, that one is caused by the
     * IOException (Room 3.0.3's ExclusiveMutex and FileLock). On iOS it has only a message, as
     * "Unable to open lock file (Is a directory): '…'" or "Unable to lock file (…)", since Room's
     * native FileLock calls POSIX directly (Room 3.0.3's FileLock.native.kt). A cancellation is
     * an IllegalStateException as well, but stays one.
     */
    private fun Throwable.isStorageFailure(): Boolean = when (this) {
        is SQLiteException -> !isDamage() && when (resultCode) {
            null, SQLITE_CONSTRAINT, SQLITE_MISMATCH, SQLITE_MISUSE, SQLITE_RANGE -> false
            else -> true
        }

        is CancellationException -> false

        is IllegalStateException -> generateSequence<Throwable>(this) { it.cause }.any {
            it is IOException || it.message?.let(LOCK_FILE_FAILURE::containsMatchIn) == true
        }

        else -> false
    }

    /**
     * Runs [action] once the badge is started, starting it with [start] first if it isn't,
     * checking and writing in one transaction.
     */
    private suspend fun <T> afterStarting(
        badgeId: String,
        start: BadgeStart,
        action: suspend () -> T
    ): T = writing {
        database.withWriteTransaction {
            if (!dao.isStarted(badgeId)) dao.insertBadge(start.progress(badgeId))
            action()
        }
    }

    /**
     * Runs [action] if the badge is started, checking and writing in one transaction, or gives
     * [notStarted] if it isn't.
     */
    private suspend fun <T> ifStarted(badgeId: String, notStarted: T, action: suspend () -> T): T =
        writing {
            database.withWriteTransaction { if (dao.isStarted(badgeId)) action() else notStarted }
        }
}

/** The start of the message of Room's error for a lock file it can't open or lock, on iOS. */
private val LOCK_FILE_FAILURE = Regex("^Unable to (open lock file|lock file) \\(")
