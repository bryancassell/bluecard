package io.github.bryancassell.bluecard.data.profile

import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior every [ProfileRepository] must have. Runs against the DataStore implementation
 * (DataStoreProfileRepositoryTest) and the fake (FakeProfileRepositoryTest).
 */
abstract class ProfileRepositoryContract {
    protected abstract val repository: ProfileRepository

    /** A repository whose stored profile can't be read. */
    protected abstract fun unreadableRepository(): ProfileRepository

    @Test
    fun observeProfile_beforeSave_isNull() = runTest {
        assertNull(repository.observeProfile().first())
    }

    @Test
    fun saveProfile_isObserved() = runTest {
        repository.saveProfile(Profile("Alex Scout", "123"))

        assertEquals(Profile("Alex Scout", "123"), repository.observeProfile().first())
    }

    @Test
    fun saveProfile_again_replacesIt() = runTest {
        repository.saveProfile(Profile("Alex Scout", "123"))
        repository.saveProfile(Profile("Sam Scout", "456"))

        assertEquals(Profile("Sam Scout", "456"), repository.observeProfile().first())
    }

    @Test
    fun observeProfile_whenUnreadable_throwsIOException() = runTest {
        val error = runCatching {
            unreadableRepository().observeProfile().first()
        }.exceptionOrNull()

        assertTrue("Expected an IOException, got $error", error is IOException)
    }
}
