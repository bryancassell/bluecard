package io.github.bryancassell.bluecard.testing

import androidx.test.core.app.ApplicationProvider
import io.github.bryancassell.bluecard.di.androidModule
import io.github.bryancassell.bluecard.di.appModule
import org.junit.rules.ExternalResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Starts Koin with the app's modules before each test, as BlueCardApplication does in the app,
 * and stops it after. Koin allows one container per process, and Robolectric runs every test in
 * one. Rules start before `@Before` methods, so those can replace definitions, as with
 * `loadKoinModules(testIoDispatcherModule)`.
 */
class AppKoinRule : ExternalResource() {
    lateinit var koin: Koin
        private set

    override fun before() {
        koin = startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(appModule, androidModule)
        }.koin
    }

    override fun after() {
        stopKoin()
    }
}
