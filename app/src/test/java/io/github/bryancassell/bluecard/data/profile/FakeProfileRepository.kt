package io.github.bryancassell.bluecard.data.profile

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

/**
 * An in-memory [ProfileRepository] for other features' tests. ProfileRepositoryContract
 * checks that it behaves like the DataStore implementation.
 */
class FakeProfileRepository(profile: Profile? = null) : ProfileRepository {
    private val profile = MutableStateFlow(profile)

    /** When true, [saveProfile] throws, as DataStore does when the disk is full. */
    var failSaves = false

    /** When true, [observeProfile] throws, as DataStore does when it can't read its file. */
    var failLoads = false

    override fun observeProfile(): Flow<Profile?> =
        profile.onStart { if (failLoads) throw IOException("Load failed") }

    /** Removes the profile, as DataStore's reset of a corrupted file does. */
    fun removeProfile() {
        profile.value = null
    }

    override suspend fun saveProfile(profile: Profile) {
        if (failSaves) throw IOException("Save failed")
        this.profile.value = profile
    }
}
