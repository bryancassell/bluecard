package io.github.bryancassell.bluecard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import dagger.hilt.android.AndroidEntryPoint
import io.github.bryancassell.bluecard.text.stringsLocale
import io.github.bryancassell.bluecard.ui.BlueCardApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    // ComponentActivity gives the extras of the intent that opened it to every ViewModel's
    // SavedStateHandle as default arguments, and Navigation 3 passes them on to every screen.
    // The activity is exported, so any app could fill a screen's saved state that way; BlueCard
    // uses neither intent extras nor default arguments, so the defaults are left empty (#83).
    // That covers every ViewModel created with these creation extras, as Hilt and Navigation 3
    // create them. The default factory still passes the extras to a ViewModel created without
    // creation extras, so create none that way; overriding the factory would replace Hilt's.
    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(super.defaultViewModelCreationExtras).apply {
            set(DEFAULT_ARGS_KEY, Bundle())
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Lay out in the strings' language's direction, not the device's, so English strings
        // read left-to-right on a Persian phone (see ARCHITECTURE.md, Language and layout
        // direction). Setting it on the activity keeps its views, Compose, focus and resources in
        // step.
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
        // BlueCardTheme is light in dark mode too, so the system bars keep dark icons. These
        // are enableEdgeToEdge's default styles, except that they never detect dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { false },
            // auto's dark scrim is only drawn below Android 8, so the light one fills both.
            navigationBarStyle = SystemBarStyle.auto(DefaultLightScrim, DefaultLightScrim) { false }
        )
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            BlueCardApp(uiState)
        }
    }
}

// enableEdgeToEdge's default light navigation bar scrim, which androidx.activity keeps internal.
// Android 8 and 9 draw it behind the navigation bar; later versions add their own when needed.
private val DefaultLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
