package io.github.bryancassell.bluecard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Marks the scope for work that must finish even if the screen that started it closes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopesModule {
    /**
     * Lives as long as the app, as the data layer guide advises for work that must outlive the
     * screen: https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
     * One coroutine failing doesn't cancel the others (SupervisorJob). Its work is saving to
     * disk, so it runs on the IO dispatcher.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@IoDispatcher ioDispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + ioDispatcher)
}
