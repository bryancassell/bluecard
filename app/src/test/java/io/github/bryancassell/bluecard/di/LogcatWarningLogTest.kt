package io.github.bryancassell.bluecard.di

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** [LogcatWarningLog], with Robolectric so logcat can be read back. */
@RunWith(AndroidJUnit4::class)
class LogcatWarningLogTest {
    @Test
    fun warn_writesAWarningWithItsError() {
        val error = IOException("Can't read")

        LogcatWarningLog.warn("LoadFailure", "Couldn't load stored data", error)

        val log = ShadowLog.getLogsForTag("LoadFailure").single()
        assertEquals(Log.WARN, log.type)
        assertEquals("Couldn't load stored data", log.msg)
        assertSame(error, log.throwable)
    }

    @Test
    fun warn_withoutAnError_writesTheMessageAlone() {
        LogcatWarningLog.warn("DamagedProgress", "Set aside a damaged database")

        val log = ShadowLog.getLogsForTag("DamagedProgress").single()
        assertEquals(Log.WARN, log.type)
        assertEquals("Set aside a damaged database", log.msg)
        assertNull(log.throwable)
    }
}
