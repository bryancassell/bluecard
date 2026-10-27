package io.github.bryancassell.bluecard.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.backup.JsonBackupRepository
import io.github.bryancassell.bluecard.data.catalog.AssetCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository
import io.github.bryancassell.bluecard.testing.AppKoinRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.qualifier.named

/** Checks what [appModule] provides, from Koin started with the app's modules. */
@RunWith(AndroidJUnit4::class)
class AppModuleTest {
    @get:Rule
    val appKoin = AppKoinRule()

    private val koin get() = appKoin.koin

    @Test
    fun ioDispatcher_isDispatchersIo() {
        assertSame(Dispatchers.IO, koin.get<CoroutineDispatcher>(named<IoDispatcher>()))
    }

    @Test
    fun clock_isDeviceClock() {
        assertSame(DeviceClock, koin.get<ZonedClock>())
    }

    @Test
    fun catalogRepository_isAssetCatalogRepository() {
        assertTrue(koin.get<CatalogRepository>() is AssetCatalogRepository)
    }

    @Test
    fun progressRepository_isRoomProgressRepository() {
        assertTrue(koin.get<ProgressRepository>() is RoomProgressRepository)
    }

    @Test
    fun profileRepository_isDataStoreProfileRepository() {
        assertTrue(koin.get<ProfileRepository>() is DataStoreProfileRepository)
    }

    @Test
    fun backupRepository_isJsonBackupRepository() {
        assertTrue(koin.get<BackupRepository>() is JsonBackupRepository)
    }
}
