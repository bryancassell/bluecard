package io.github.bryancassell.bluecard

import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.di.appModule
import io.github.bryancassell.bluecard.di.iosModule
import io.github.bryancassell.bluecard.ui.BlueCardApp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * Starts Koin, which builds the app's repositories and ViewModels as they're first needed. The
 * iOS app calls it once, as it starts, before [MainViewController]. Swift sees it as
 * `doInitKoin()`, since Objective-C keeps names that start with "init" for initializers.
 */
fun initKoin() {
    startKoin { modules(appModule, iosModule) }
}

/**
 * The app's content, in the iOS app's one window. Until the profile loads, it shows the empty
 * page that follows the launch screen, since iOS can't keep that up as Android keeps its splash
 * screen, so the app still opens straight on Onboarding or Home.
 */
@Suppress("FunctionName") // Named as the view controller it returns, as JetBrains' templates do.
fun MainViewController(): UIViewController = ComposeUIViewController {
    val viewModel = koinViewModel<MainActivityViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BlueCardApp(uiState, koinInject(), viewModel::dismissDamagedProgressNotice)
}
