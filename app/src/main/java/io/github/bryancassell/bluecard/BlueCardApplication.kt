package io.github.bryancassell.bluecard

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.StrictMode
import io.github.bryancassell.bluecard.di.androidModule
import io.github.bryancassell.bluecard.di.appModule
import io.github.bryancassell.bluecard.text.useStringsLocalesByDefault
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Starts Koin, which builds the app's repositories and ViewModels as they're first needed, and
 * keeps the strings' locales the default.
 */
class BlueCardApplication : Application() {
    override fun onCreate() {
        // Debug builds report disk access on the main thread, objects never closed and
        // leaked activities as they happen. They only log and flash the screen, never
        // crash: detectAll() turns on new checks as targetSdk rises, Now in Android removed
        // penaltyDeath() after crashes from code it doesn't own, and under Robolectric a VM
        // policy's penaltyDeath() ends the whole test run. It checks FLAG_DEBUGGABLE, since AGP
        // no longer generates BuildConfig by default. Set before Koin starts, so that's
        // checked too.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build()
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        super.onCreate()
        startKoin {
            androidContext(this@BlueCardApplication)
            modules(appModule, androidModule)
        }
    }

    // Android sets the default locales back to the device's on each configuration change, and
    // the strings pick their plural forms by them (useStringsLocalesByDefault). This runs right
    // after, before any activity the change recreates. It blocks to read one string, as
    // MainActivity does.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runBlocking { useStringsLocalesByDefault(this@BlueCardApplication) }
    }

    // Robolectric starts the app again for each test, in the same process, and ends each test
    // with onTerminate(), which a phone never calls. Koin allows one container per process, so
    // it's stopped here for the next test's app to start its own.
    override fun onTerminate() {
        stopKoin()
        super.onTerminate()
    }
}
