package io.github.bryancassell.bluecard.di

import java.util.TimeZone
import kotlin.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClockTest {
    private val deviceZone = TimeZone.getDefault()

    @After
    fun restoreDeviceZone() {
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun timeZone_isDevicesTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(PAGO_PAGO))

        assertEquals(PAGO_PAGO, DeviceClock.timeZone.id)
    }

    @Test
    fun today_afterDevicesTimeZoneChanges_isInTheNewZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(PAGO_PAGO))
        val clock = DeviceClock
        val todayBefore = clock.today()

        TimeZone.setDefault(TimeZone.getTimeZone(KIRITIMATI))

        assertEquals(KIRITIMATI, clock.timeZone.id)
        // Kiritimati's date is always a day or two ahead of Pago Pago's.
        assertTrue(clock.today() > todayBefore)
    }

    @Test
    fun now_isTheSystemTime() {
        val before = Clock.System.now()
        val read = DeviceClock.now()
        val after = Clock.System.now()

        assertTrue(read in before..after)
    }

    private companion object {
        // UTC−11 and UTC+14, neither with daylight saving time, so their dates always differ.
        const val PAGO_PAGO = "Pacific/Pago_Pago"
        const val KIRITIMATI = "Pacific/Kiritimati"
    }
}
