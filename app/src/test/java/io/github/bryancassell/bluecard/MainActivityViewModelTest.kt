package io.github.bryancassell.bluecard

import io.github.bryancassell.bluecard.data.profile.FakeProfileRepository
import io.github.bryancassell.bluecard.data.profile.Profile
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import io.github.bryancassell.bluecard.ui.navigation.Home
import io.github.bryancassell.bluecard.ui.navigation.Onboarding
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
    fun uiState_withoutProfile_startsAtOnboarding() = runTest {
        val viewModel = MainActivityViewModel(FakeProfileRepository())
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.Ready(Onboarding), viewModel.uiState.value)
    }

    @Test
    fun uiState_withProfile_startsAtHome() = runTest {
        val viewModel = MainActivityViewModel(FakeProfileRepository(Profile("Alex Scout", "123")))
        startCollecting(viewModel)

        assertEquals(MainActivityUiState.Ready(Home), viewModel.uiState.value)
    }
}
