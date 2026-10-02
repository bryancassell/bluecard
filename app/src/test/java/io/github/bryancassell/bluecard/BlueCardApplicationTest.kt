package io.github.bryancassell.bluecard

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.LeakedClosableViolation
import android.os.strictmode.Violation
import android.util.CloseGuard
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

/**
 * StrictMode in [BlueCardApplication], which Robolectric starts from the manifest. Local tests
 * run against the debug build, which is debuggable, so each violation is logged.
 */
@RunWith(AndroidJUnit4::class)
class BlueCardApplicationTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun debugBuild_isDebuggable() {
        assertNotEquals(0, application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
    }

    @Test
    fun diskReadOnMainThread_isLogged() {
        application.getSharedPreferences("strict-mode", Context.MODE_PRIVATE).getString("key", null)
        // StrictMode logs a main-thread violation once the main thread is idle.
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(strictModeLogged<DiskReadViolation>())
    }

    @Test
    fun objectNeverClosed_isLogged() {
        // What a stream or cursor does when it's garbage collected without being closed.
        CloseGuard().apply { open("close") }.warnIfOpen()

        assertTrue(strictModeLogged<LeakedClosableViolation>())
    }

    // StrictMode writes the violation's class name and stack trace into the message.
    private inline fun <reified T : Violation> strictModeLogged(): Boolean =
        ShadowLog.getLogsForTag("StrictMode").any { T::class.java.name in it.msg }
}
