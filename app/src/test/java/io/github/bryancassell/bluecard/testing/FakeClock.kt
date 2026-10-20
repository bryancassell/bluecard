package io.github.bryancassell.bluecard.testing

import io.github.bryancassell.bluecard.di.ZonedClock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** A clock in UTC that reads [now], which a test can move on, such as past midnight. */
class FakeClock(var now: Instant) : ZonedClock {
    override val timeZone: TimeZone = TimeZone.UTC

    override fun now(): Instant = now
}
