package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Replaces the IO dispatcher in the Koin that BlueCardApplication starts, so the real
 * repositories a test gets from it work on the test's thread, not on background threads that
 * outlive the test. Load it before anything is built from Koin:
 * `loadKoinModules(testIoDispatcherModule)`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
val testIoDispatcherModule = module {
    single<CoroutineDispatcher>(named<IoDispatcher>()) { UnconfinedTestDispatcher() }
}
