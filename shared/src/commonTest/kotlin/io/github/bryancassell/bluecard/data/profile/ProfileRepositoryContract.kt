package io.github.bryancassell.bluecard.data.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.IOException

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

    // Half an emoji, as pasted or imported, stored as the app stores all the scout's text.
    @Test
    fun saveProfile_trimsSpaces_andReplacesHalfAnEmoji() = runTest {
        repository.saveProfile(Profile(" Alex\uD83D Scout ", "\uDC22123 "))

        assertEquals(Profile("Alex\uFFFD Scout", "\uFFFD123"), repository.observeProfile().first())
    }

    @Test
    fun observeProfile_whenUnreadable_throwsIOException() = runTest {
        val error = runCatching {
            unreadableRepository().observeProfile().first()
        }.exceptionOrNull()

        assertTrue(error is IOException, "Expected an IOException, got $error")
    }
}
