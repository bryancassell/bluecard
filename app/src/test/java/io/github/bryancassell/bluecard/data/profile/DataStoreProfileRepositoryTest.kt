package io.github.bryancassell.bluecard.data.profile

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs the repository contract against DataStore, with its file in a temporary folder. */
class DataStoreProfileRepositoryTest : ProfileRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "profile.preferences_pb")
    private val jobs = mutableListOf<Job>()

    /** A new DataStore on [file], as a new app process would create. */
    private fun newRepository(produceFile: () -> File = { file }): DataStoreProfileRepository {
        val job = Job().also { jobs += it }
        val dataStore = DataStoreProfileRepository.createDataStore(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = produceFile
        )
        return DataStoreProfileRepository(dataStore)
    }

    override val repository = newRepository()

    // A folder where the file should be, so DataStore can't read it.
    override fun unreadableRepository() =
        newRepository { folder.newFolder("unreadable.preferences_pb") }

    @After
    fun closeDataStores() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
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
        file.writeText("not a preferences file")
        val reopened = newRepository()

        assertNull(reopened.observeProfile().first())

        // Saving works again, so the scout can redo Onboarding.
        reopened.saveProfile(Profile("Alex Scout", "123"))
        assertEquals(Profile("Alex Scout", "123"), reopened.observeProfile().first())
    }
}
