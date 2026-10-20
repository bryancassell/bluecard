package io.github.bryancassell.bluecard.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/** Checks what [appModule] provides, from the Koin that BlueCardApplication starts. */
@RunWith(AndroidJUnit4::class)
class AppModuleTest {
    private val koin = GlobalContext.get()

    @Test
    fun ioDispatcher_isDispatchersIo() {
        assertSame(Dispatchers.IO, koin.get<CoroutineDispatcher>(named<IoDispatcher>()))
    }

    @Test
    fun clock_isDeviceClock() {
        assertSame(DeviceClock, koin.get<ZonedClock>())
    }

    @Test
    fun progressRepository_isRoomProgressRepository() {
        assertTrue(koin.get<ProgressRepository>() is RoomProgressRepository)
    }

    @Test
    fun profileRepository_isDataStoreProfileRepository() {
        assertTrue(koin.get<ProfileRepository>() is DataStoreProfileRepository)
    }
}
