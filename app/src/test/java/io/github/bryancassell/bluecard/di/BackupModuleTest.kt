package io.github.bryancassell.bluecard.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.backup.JsonBackupRepository
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks that Hilt provides the production backup implementation. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class BackupModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var backupRepository: BackupRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun backupRepository_isJsonBackupRepository() {
        assertTrue(backupRepository is JsonBackupRepository)
    }
}
