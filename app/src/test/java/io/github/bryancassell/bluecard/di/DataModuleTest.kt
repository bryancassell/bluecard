package io.github.bryancassell.bluecard.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.bryancassell.bluecard.data.catalog.AssetCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks that Hilt provides the production repository implementations. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DataModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var catalogRepository: CatalogRepository

    @Inject
    lateinit var progressRepository: ProgressRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun catalogRepository_isAssetCatalogRepository() {
        assertTrue(catalogRepository is AssetCatalogRepository)
    }

    @Test
    fun progressRepository_isRoomProgressRepository() {
        assertTrue(progressRepository is RoomProgressRepository)
    }
}
