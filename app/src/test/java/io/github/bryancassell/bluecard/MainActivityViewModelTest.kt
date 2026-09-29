package io.github.bryancassell.bluecard

import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
        val viewModel = MainActivityViewModel(loading)
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProfileCantBeRead_isLoadFailed() = runTest {
        val viewModel = MainActivityViewModel(FakeProfileRepository().apply { failLoads = true })
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.LoadFailed, viewModel.uiState.value)
    }

    @Test
    fun uiState_withoutProfile_isNotSetUp() = runTest {
        val viewModel = MainActivityViewModel(FakeProfileRepository())
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.Ready(isSetUp = false), viewModel.uiState.value)
    }

    @Test
    fun uiState_withProfile_isSetUp() = runTest {
        val viewModel = MainActivityViewModel(FakeProfileRepository(Profile("Alex Scout", "123")))
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.Ready(isSetUp = true), viewModel.uiState.value)
    }

    @Test
    fun uiState_whenProfileIsSaved_becomesSetUp() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = MainActivityViewModel(repository)
        startCollecting(viewModel)

        repository.saveProfile(Profile("Alex Scout", "123"))

        assertEquals(MainActivityUiState.Ready(isSetUp = true), viewModel.uiState.value)
    }
}
