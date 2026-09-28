package io.github.bryancassell.bluecard.data.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
    private fun newRepository(): DataStoreProfileRepository {
        val job = Job().also { jobs += it }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file }
        )
        return DataStoreProfileRepository(dataStore)
    }

    override val repository = newRepository()

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
}
