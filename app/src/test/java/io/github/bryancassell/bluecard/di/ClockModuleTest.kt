package io.github.bryancassell.bluecard.di

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockModuleTest {
    private val deviceZone = TimeZone.getDefault()

    @After
    fun restoreDeviceZone() {
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun clock_isInDevicesTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(PAGO_PAGO))

        assertEquals(PAGO_PAGO, ClockModule.provideClock().zone)
    }

    @Test
    fun clock_afterDevicesTimeZoneChanges_isInTheNewZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(PAGO_PAGO))
        val clock = ClockModule.provideClock()
        val todayBefore = LocalDate.now(clock)

        TimeZone.setDefault(TimeZone.getTimeZone(KIRITIMATI))

        assertEquals(KIRITIMATI, clock.zone)
        // Kiritimati's date is always a day or two ahead of Pago Pago's.
        assertTrue(LocalDate.now(clock).isAfter(todayBefore))
    }

    @Test
    fun clock_isTheSystemTime() {
        val before = Instant.now()
        val read = ClockModule.provideClock().instant()
        val after = Instant.now()

        assertTrue(read in before..after)
    }

    @Test
    fun withZone_isTheSystemClockInThatZone() {
        assertEquals(Clock.system(KIRITIMATI), ClockModule.provideClock().withZone(KIRITIMATI))
    }

    private companion object {
        // UTC−11 and UTC+14, neither with daylight saving time, so their dates always differ.
        val PAGO_PAGO: ZoneId = ZoneId.of("Pacific/Pago_Pago")
        val KIRITIMATI: ZoneId = ZoneId.of("Pacific/Kiritimati")
    }
}
