package io.github.bryancassell.bluecard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * Provides the clock that dates such as "today" are read from, so tests can fix the date.
 * Not a singleton: each class that injects it gets the device's time zone at that moment.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
