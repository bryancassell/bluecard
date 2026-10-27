package io.github.bryancassell.bluecard.data.profile

import kotlinx.coroutines.flow.Flow

/** The scout's name and unit number, entered on first launch and changeable later. */
interface ProfileRepository {
    /**
     * The saved profile, or null until first-launch setup saves one. Throws an `IOException`
     * if the profile can't be read.
     */
    fun observeProfile(): Flow<Profile?>

    /**
     * Saves the profile, replacing any saved before. It finishes even if the caller is
     * cancelled, such as when the scout leaves the screen. Throws an `IOException` if the
     * profile can't be saved.
     */
    suspend fun saveProfile(profile: Profile)
}
