package io.github.bryancassell.bluecard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Provides the clock that dates such as "today" are read from, so tests can fix the date. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = DeviceClock
}

/**
 * The system clock in the device's time zone at the moment it's read. [Clock.systemDefaultZone]
 * keeps the zone it was made in, so a page left open through a time zone change, as when a scout
 * travels to camp, would go on using the old zone for "today" (#79). A "today" held in a page's
 * UI state, such as the date picker's latest date, still catches up only the next time that
 * state updates.
 */
private object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
