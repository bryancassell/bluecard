package io.github.bryancassell.bluecard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.bryancassell.bluecard.ui.navigation.BlueCardNavDisplay
import io.github.bryancassell.bluecard.ui.theme.BlueCardTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must come before super.onCreate(). Keep the splash screen up until the profile
        // loads, so the app opens straight on Onboarding or Home.
        installSplashScreen().setKeepOnScreenCondition {
            viewModel.uiState.value == MainActivityUiState.Loading
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            BlueCardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    (uiState as? MainActivityUiState.Ready)?.let {
                        BlueCardNavDisplay(
                            startDestination = it.startDestination,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
