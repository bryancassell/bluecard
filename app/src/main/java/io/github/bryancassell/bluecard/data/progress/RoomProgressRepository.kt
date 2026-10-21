package io.github.bryancassell.bluecard.data.progress

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import okio.IOException

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
class RoomProgressRepository(
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
        throw if (it.isStorageFailure()) IOException(it) else it
    }

    /**
     * Runs [write] in [externalScope] and waits for it. If the caller is cancelled, only the
     * wait is: the write still finishes ([runOutlivingCaller]). Reports a database that can't be
     * written, such as one that can't be opened or a full disk, as an [IOException], in the same
     * way as [readFailuresAsIOException]. Any other exception is a bug: the caller gets it too,
     * but it crashes the app through [externalScope] even if the caller is gone.
     */
    // Started in the caller's thread, so writes queue for the database's writeOrder in the order
    // they're made.
    private suspend fun <T> writing(write: suspend () -> T): T = externalScope.runOutlivingCaller {
        try {
            database.writeOrder.withLock { write() }
        } catch (e: Exception) {
            throw if (e.isStorageFailure()) IOException(e) else e
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
     * Damage crashes the app too, and the next launch sets the database aside
     * ([SetAsideDamagedDatabaseDriver]). Reporting it as a load or save failure would leave the
     * scout retrying on a database that stays damaged until the app restarts.
     *
     * Before Room first opens the database, it locks a file beside it, and reports a lock file
     * it can't create or lock as an IllegalStateException caused by the IOException (checked in
     * Room 3.0.3's ExclusiveMutex and FileLock), so that's a storage failure too. A cancellation
     * is an IllegalStateException as well, but stays one.
     */
    private fun Throwable.isStorageFailure(): Boolean = when (this) {
        is SQLiteException -> !isDamage() && when (resultCode) {
            null, SQLITE_CONSTRAINT, SQLITE_MISMATCH, SQLITE_MISUSE, SQLITE_RANGE -> false
            else -> true
        }

        is CancellationException -> false

        is IllegalStateException -> generateSequence(cause) { it.cause }.any { it is IOException }

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
