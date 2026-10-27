package io.github.bryancassell.bluecard.di

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** A clock with the time zone its dates, such as today's, are read in. */
interface ZonedClock : Clock {
    /**
     * The zone now. Read it each time it's needed, rather than keeping it, as the device's can
     * change while a page is open.
     */
    val timeZone: TimeZone

    fun today(): LocalDate = todayIn(timeZone)
}

/**
 * The system clock in the device's time zone at the moment it's read. A zone read once, when the
 * clock was made, would go on giving "today" in the old zone on a page left open through a time
 * zone change, as when a scout travels to camp (#79).
 */
internal object DeviceClock : ZonedClock {
    override val timeZone: TimeZone get() = TimeZone.currentSystemDefault()

    override fun now(): Instant = Clock.System.now()
}
