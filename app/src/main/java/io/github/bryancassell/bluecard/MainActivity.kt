package io.github.bryancassell.bluecard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.bryancassell.bluecard.ui.BlueCardApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate(). Keep the splash screen up until the profile
        // loads, or fails to load, so the app opens straight on Onboarding or Home.
        installSplashScreen().setKeepOnScreenCondition {
            viewModel.uiState.value == MainActivityUiState.Loading
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            BlueCardApp(uiState)
        }
    }
}
