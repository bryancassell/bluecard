package io.github.bryancassell.bluecard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bryancassell.bluecard.di.NSLogWarningLog
import io.github.bryancassell.bluecard.di.appModule
import io.github.bryancassell.bluecard.di.iosModule
import io.github.bryancassell.bluecard.text.useStringsLanguageByDefault
import io.github.bryancassell.bluecard.ui.BlueCardApp
import io.github.bryancassell.bluecard.ui.SceneSavedState
import io.github.bryancassell.bluecard.ui.appBuild
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
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
 * screen, so the app still opens straight on Onboarding or Home. The strings' language is made
 * the preferred one first ([useStringsLanguageByDefault]), as Android's `MainActivity` does.
 *
 * The pages start as [savedState], the text [onSaveState] last gave, which the iOS app keeps
 * with its scene ([SceneSavedState]), or an empty string. It's given each time the scene goes
 * inactive (ON_PAUSE), as on its way to the background. Given only at ON_STOP, as the scene
 * entered the background, it reached the copy iOS keeps only on the app's next trip there
 * (checked on an iOS 27.0 simulator), so iOS ending the app restored the page before. Apple's
 * sample "Restoring your app's state" also saves its state as the scene becomes inactive.
 *
 * It's given at ON_STOP too, so a scene that went to the background without becoming active,
 * as when the scout leaves during launch, says its pages are no longer open. What changes after
 * then reaches the copy iOS keeps only on the next trip, or when the scout closes the app in the
 * app switcher. So if iOS ends the app first, a page that closed as its save finished comes back
 * as the scout left it, as it can on Android in a shorter time, since Android saves after
 * onStop. A new log entry's page then closes, since its entry was added
 * (TrackerEntryViewModel.closeIfAdded), and an edit page shows the saved text.
 */
@Suppress("FunctionName") // Named as the view controller it returns, as JetBrains' templates do.
fun MainViewController(savedState: String, onSaveState: (String) -> Unit): UIViewController {
    useStringsLanguageByDefault()
    val sceneState = SceneSavedState(
        savedState,
        appBuild { NSBundle.mainBundle.objectForInfoDictionaryKey(it) },
        NSUserDefaults.standardUserDefaults,
        NSLogWarningLog
    )
    return ComposeUIViewController {
        CompositionLocalProvider(LocalSaveableStateRegistry provides sceneState.registry) {
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { sceneState.resumed() }
            LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { onSaveState(sceneState.paused()) }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onSaveState(sceneState.paused()) }
            val viewModel = koinViewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            BlueCardApp(uiState, koinInject(), viewModel::dismissDamagedProgressNotice)
        }
    }
}
