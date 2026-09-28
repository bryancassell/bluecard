package io.github.bryancassell.bluecard.ui.home

import io.github.bryancassell.bluecard.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_startsWithPlaceholder() {
        val viewModel = HomeViewModel()
        assertEquals(HomeUiState, viewModel.uiState.value)
    }
}
