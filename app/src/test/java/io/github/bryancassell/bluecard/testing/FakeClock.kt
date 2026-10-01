package io.github.bryancassell.bluecard.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock in UTC that reads [now], which a test can move on, such as past midnight. */
class FakeClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()

    override fun instant(): Instant = now
}
