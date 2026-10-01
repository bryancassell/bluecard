package io.github.bryancassell.bluecard.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.bryancassell.bluecard.data.runOutlivingCaller
import io.github.bryancassell.bluecard.di.ApplicationScope
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [ProfileRepository] backed by Preferences DataStore. Saving runs in [externalScope], so the
 * profile finishes saving even if the scout leaves the screen ([runOutlivingCaller]).
 */
class DataStoreProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:ApplicationScope private val externalScope: CoroutineScope
) : ProfileRepository {
    override fun observeProfile(): Flow<Profile?> = dataStore.data.map { preferences ->
        val name = preferences[NAME] ?: return@map null
        val unitNumber = preferences[UNIT_NUMBER] ?: return@map null
        Profile(name, unitNumber)
    }

    override suspend fun saveProfile(profile: Profile) {
        externalScope.runOutlivingCaller {
            dataStore.edit {
                it[NAME] = profile.name
                it[UNIT_NUMBER] = profile.unitNumber
            }
        }
    }

    companion object {
        /** The DataStore file's name, without the `.preferences_pb` extension. */
        const val FILE_NAME = "profile"

        private val NAME = stringPreferencesKey("name")
        private val UNIT_NUMBER = stringPreferencesKey("unit_number")

        /**
         * Creates the profile's DataStore. A corrupted file is replaced with an empty one,
         * so the scout goes through Onboarding again instead of the app failing on every
         * launch; without a handler, DataStore throws on every read. See "Handle file
         * corruption" in https://developer.android.com/topic/libraries/architecture/datastore
         */
        fun createDataStore(
            scope: CoroutineScope,
            produceFile: () -> File
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = produceFile
        )
    }
}
