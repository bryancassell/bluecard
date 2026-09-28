package io.github.bryancassell.bluecard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Replaces [DispatchersModule] in every `@HiltAndroidTest`, so code running in Hilt tests
 * uses a test dispatcher instead of real background threads. Every dispatcher binding is
 * the same [TestDispatcher], which a test can inject to control its virtual time.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DispatchersModule::class])
object TestDispatchersModule {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideTestDispatcher(): TestDispatcher = UnconfinedTestDispatcher()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(testDispatcher: TestDispatcher): CoroutineDispatcher = testDispatcher
}
