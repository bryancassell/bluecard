package io.github.bryancassell.bluecard

import android.content.Context
import android.content.res.Configuration
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
import io.github.bryancassell.bluecard.ui.stringsLocale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Lay out in the strings' language's direction, not the device's, so English strings
        // read left-to-right on a Persian phone (see ARCHITECTURE.md, UI layer). Setting it on
        // the activity keeps its views, Compose, focus and resources in step.
        applyOverrideConfiguration(
            Configuration().apply { setLayoutDirection(stringsLocale(newBase)) }
        )
    }

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
