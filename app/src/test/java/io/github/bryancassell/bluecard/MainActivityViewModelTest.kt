package io.github.bryancassell.bluecard

import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.FakeDamagedProgressRepository
import io.github.bryancassell.bluecard.testing.FakeWarningLog
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val warningLog = FakeWarningLog()

    /**
     * Collects uiState, as the activity does, so WhileSubscribed starts it. From the
     * coroutines testing guide: https://developer.android.com/kotlin/coroutines/test#statein
     */
    private fun TestScope.startCollecting(viewModel: MainActivityViewModel) {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
    }

    @Test
    fun uiState_whileProfileLoads_isLoading() = runTest {
        val loading = object : ProfileRepository {
            override fun observeProfile(): Flow<Profile?> = flow { awaitCancellation() }

            override suspend fun saveProfile(profile: Profile) = Unit
        }
        val viewModel = MainActivityViewModel(loading, FakeDamagedProgressRepository(), warningLog)
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProfileCantBeRead_isLoadFailed() = runTest {
        val viewModel = MainActivityViewModel(
            FakeProfileRepository().apply { failLoads = true },
            FakeDamagedProgressRepository(),
            warningLog
        )
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_withoutProfile_isNotSetUp() = runTest {
        val viewModel =
            MainActivityViewModel(
                FakeProfileRepository(),
                FakeDamagedProgressRepository(),
                warningLog
            )
        startCollecting(viewModel)

        assertEquals(
            MainActivityUiState.Ready(isSetUp = false, showDamagedProgressNotice = false),
            viewModel.uiState.value
        )
    }

    @Test
    fun uiState_withProfile_isSetUp() = runTest {
        val viewModel = MainActivityViewModel(
            FakeProfileRepository(Profile("Alex Scout", "123")),
            FakeDamagedProgressRepository(),
            warningLog
        )
        startCollecting(viewModel)

        assertEquals(
            MainActivityUiState.Ready(isSetUp = true, showDamagedProgressNotice = false),
            viewModel.uiState.value
        )
    }

    @Test
    fun uiState_whenProfileIsSaved_becomesSetUp() = runTest {
        val repository = FakeProfileRepository()
        val viewModel =
            MainActivityViewModel(repository, FakeDamagedProgressRepository(), warningLog)
        startCollecting(viewModel)

        repository.saveProfile(Profile("Alex Scout", "123"))

        assertEquals(
            MainActivityUiState.Ready(isSetUp = true, showDamagedProgressNotice = false),
            viewModel.uiState.value
        )
    }

    @Test
    fun uiState_whenProgressWasSetAside_showsNotice() = runTest {
        val viewModel = MainActivityViewModel(
            FakeProfileRepository(Profile("Alex Scout", "123")),
            FakeDamagedProgressRepository(noticePending = true),
            warningLog
        )
        startCollecting(viewModel)

        assertEquals(
            MainActivityUiState.Ready(isSetUp = true, showDamagedProgressNotice = true),
            viewModel.uiState.value
        )
    }

    @Test
    fun uiState_whenProgressIsSetAsideWhileOpen_showsNotice() = runTest {
        val damagedProgress = FakeDamagedProgressRepository()
        val viewModel = MainActivityViewModel(
            FakeProfileRepository(Profile("Alex Scout", "123")),
            damagedProgress,
            warningLog
        )
        startCollecting(viewModel)

        damagedProgress.setAside()

        assertEquals(
            MainActivityUiState.Ready(isSetUp = true, showDamagedProgressNotice = true),
            viewModel.uiState.value
        )
    }

    @Test
    fun dismissDamagedProgressNotice_hidesIt_andRecordsItSeen() = runTest {
        val damagedProgress = FakeDamagedProgressRepository(noticePending = true)
        val viewModel = MainActivityViewModel(
            FakeProfileRepository(Profile("Alex Scout", "123")),
            damagedProgress,
            warningLog
        )
        startCollecting(viewModel)

        viewModel.dismissDamagedProgressNotice()

        assertEquals(
            MainActivityUiState.Ready(isSetUp = true, showDamagedProgressNotice = false),
            viewModel.uiState.value
        )
        assertFalse(damagedProgress.observeNoticePending().first())
    }
}
