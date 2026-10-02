package io.github.bryancassell.bluecard

import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.os.strictmode.CustomViolation
import android.os.strictmode.LeakedClosableViolation
import android.os.strictmode.Violation
import android.util.CloseGuard
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/**
 * StrictMode in [BlueCardApplication], which Robolectric starts from the manifest before each
 * test. StrictMode's policies outlive the test that set them, so each test turns them off, sets
 * whether the app is debuggable, and starts the app again.
 */
@RunWith(AndroidJUnit4::class)
class BlueCardApplicationTest {
    private val application = ApplicationProvider.getApplicationContext<BlueCardApplication>()

    @Test
    fun debuggableApp_logsMainThreadViolations() {
        startApp(debuggable = true)

        assertTrue(mainThreadViolationLogged())
    }

    @Test
    fun debuggableApp_logsObjectsNeverClosed() {
        startApp(debuggable = true)

        assertTrue(objectNeverClosedLogged())
    }

    @Test
    fun releaseApp_leavesStrictModeOff() {
        startApp(debuggable = false)

        assertFalse(mainThreadViolationLogged())
        assertFalse(objectNeverClosedLogged())
    }

    private fun startApp(debuggable: Boolean) {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
        val info = application.applicationInfo
        info.flags = if (debuggable) {
            info.flags or ApplicationInfo.FLAG_DEBUGGABLE
        } else {
            info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        }
        application.onCreate()
    }

    /**
     * Notes a violation under the main thread's policy. On the main thread, StrictMode logs only
     * once the looper runs a message it posts, and Robolectric drops that message if a test ends
     * first, so it's noted on a thread without a looper, where StrictMode logs at once.
     */
    private fun mainThreadViolationLogged(): Boolean {
        val policy = StrictMode.getThreadPolicy()
        thread {
            StrictMode.setThreadPolicy(policy)
            StrictMode.noteSlowCall("A slow call")
        }.join()
        return strictModeLogged<CustomViolation>()
    }

    /**
     * What a stream or cursor does when it's garbage collected without being closed. StrictMode
     * logs a violation from the same place only once a second, and Robolectric's clock restarts
     * with each test, so a test run twice in one JVM would see nothing. A new close method name
     * each time makes each violation new.
     */
    private fun objectNeverClosedLogged(): Boolean {
        CloseGuard().apply { open("close${System.nanoTime()}") }.warnIfOpen()
        return strictModeLogged<LeakedClosableViolation>()
    }

    // StrictMode writes the violation's class name and stack trace into the message.
    private inline fun <reified T : Violation> strictModeLogged(): Boolean =
        ShadowLog.getLogsForTag("StrictMode").any { T::class.java.name in it.msg }
}
