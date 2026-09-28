package io.github.bryancassell.bluecard.data.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory [ProfileRepository] for other features' tests. ProfileRepositoryContract
 * checks that it behaves like the DataStore implementation.
 */
class FakeProfileRepository(profile: Profile? = null) : ProfileRepository {
    private val profile = MutableStateFlow(profile)

    override fun observeProfile(): Flow<Profile?> = profile

    override suspend fun saveProfile(profile: Profile) {
        this.profile.value = profile
    }
}
