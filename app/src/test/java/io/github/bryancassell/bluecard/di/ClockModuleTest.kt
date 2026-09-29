package io.github.bryancassell.bluecard.di

import java.time.Clock
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockModuleTest {
    @Test
    fun clock_isSystemClockInDevicesTimeZone() {
        assertEquals(Clock.system(ZoneId.systemDefault()), ClockModule.provideClock())
    }
}
