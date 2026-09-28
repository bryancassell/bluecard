package io.github.bryancassell.bluecard.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks the production profile from ProfileModule and DataStoreModule. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ProfileModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun profileRepository_isDataStoreProfileRepository() {
        assertTrue(profileRepository is DataStoreProfileRepository)
    }

    @Test
    fun profile_isStoredInProfileFile() {
        runBlocking { profileRepository.saveProfile(Profile("Alex Scout", "123")) }

        // Installed apps keep their profile here: renaming the file would lose it on update.
        val filesDir = ApplicationProvider.getApplicationContext<Context>().filesDir
        assertTrue(File(filesDir, "datastore/profile.preferences_pb").exists())
    }
}
