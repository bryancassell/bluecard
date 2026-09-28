package io.github.bryancassell.bluecard.data.profile

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory [ProfileRepository] for other features' tests. ProfileRepositoryContract
 * checks that it behaves like the DataStore implementation.
 */
class FakeProfileRepository(profile: Profile? = null) : ProfileRepository {
    private val profile = MutableStateFlow(profile)

    /** When true, [saveProfile] throws, as DataStore does when the disk is full. */
    var failSaves = false

    override fun observeProfile(): Flow<Profile?> = profile

    override suspend fun saveProfile(profile: Profile) {
        if (failSaves) throw IOException("Save failed")
        this.profile.value = profile
    }
}
