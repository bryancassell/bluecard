package io.github.bryancassell.bluecard.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.bryancassell.bluecard.data.systemFileSystem
import io.github.bryancassell.bluecard.testing.TemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path

/** Runs the repository contract against DataStore, with its file in a temporary folder. */
class DataStoreProfileRepositoryTest : ProfileRepositoryContract() {
    private val folder = TemporaryDirectory()
    private val file = folder.path / "profile.preferences_pb"
    private val jobs = mutableListOf<Job>()

    /** What escaped [externalScope]'s coroutines, which crashes the app outside tests. */
    private val uncaught = mutableListOf<Throwable>()

    // As the app's scope, which outlives the screens.
    private val externalScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e }
    )

    /** A new DataStore on [file], as a new app process would create. */
    private fun newDataStore(produceFile: () -> Path = { file }): DataStore<Preferences> {
        val job = Job().also { jobs += it }
        return DataStoreProfileRepository.createDataStore(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = produceFile
        )
    }

    private fun newRepository(produceFile: () -> Path = { file }) =
        DataStoreProfileRepository(newDataStore(produceFile), externalScope)

    override val repository = newRepository()

    // A folder where the file should be, so DataStore can't read it. Created here rather
    // than in produceFile, where a second call would throw an IOException of its own.
    override fun unreadableRepository(): DataStoreProfileRepository {
        val unreadable = folder.path / "unreadable.preferences_pb"
        systemFileSystem.createDirectory(unreadable, mustCreate = true)
        return newRepository { unreadable }
    }

    @AfterTest
    fun closeDataStores() {
        try {
            externalScope.cancel()
            runBlocking { jobs.forEach { it.cancelAndJoin() } }
        } finally {
            folder.delete()
        }
    }

    @Test
    fun savedProfile_survivesRestart() = runTest {
        repository.saveProfile(Profile("Alex Scout", "123"))
        // DataStore allows one active instance per file, so close this one first.
        jobs.single().cancelAndJoin()

        assertEquals(Profile("Alex Scout", "123"), newRepository().observeProfile().first())
    }

    @Test
    fun corruptedFile_isReplacedWithNoProfile() = runTest {
        repository.saveProfile(Profile("Alex Scout", "123"))
        jobs.single().cancelAndJoin()
        systemFileSystem.write(file) { writeUtf8("not a preferences file") }
        val reopened = newRepository()

        assertNull(reopened.observeProfile().first())

        // Saving works again, so the scout can redo Onboarding.
        reopened.saveProfile(Profile("Alex Scout", "123"))
        assertEquals(Profile("Alex Scout", "123"), reopened.observeProfile().first())
    }

    @Test
    fun saveProfile_finishesEvenIfItsCallerIsCancelled() = runTest(timeout = 10.seconds) {
        // A file of its own: DataStore allows one active instance per file, and repository has one.
        val dataStore = newDataStore { folder.path / "held.preferences_pb" }
        val saving = CompletableDeferred<Unit>()
        val written = CompletableDeferred<Unit>()
        // Holds the save until the caller is gone, as when the scout saves and leaves the page.
        val held = object : DataStore<Preferences> by dataStore {
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences {
                saving.await()
                return dataStore.updateData(transform).also { written.complete(Unit) }
            }
        }
        val repository = DataStoreProfileRepository(held, externalScope)

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.saveProfile(Profile("Alex Scout", "123"))
        }
        caller.cancel()
        saving.complete(Unit)
        // Reads once the write is done: a new DataStore read while its first write runs can miss
        // the write (about 1 run in 70).
        written.await()

        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }

    @Test
    fun saveProfile_whenItCantBeSaved_throwsIOException_withoutCrashing() = runTest {
        val dataStore = newDataStore { folder.path / "full.preferences_pb" }
        // As when the disk is full.
        val full = object : DataStore<Preferences> by dataStore {
            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences = throw IOException("Disk full")
        }
        val repository = DataStoreProfileRepository(full, externalScope)

        val error = runCatching {
            repository.saveProfile(Profile("Alex Scout", "123"))
        }.exceptionOrNull()

        assertTrue(error is IOException, "Expected an IOException, got $error")
        assertEquals(emptyList<Throwable>(), uncaught)
    }
}
