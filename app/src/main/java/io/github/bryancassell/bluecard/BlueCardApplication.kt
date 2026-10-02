package io.github.bryancassell.bluecard

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

/** Starts Hilt's dependency graph for the whole app. */
@HiltAndroidApp
class BlueCardApplication : Application() {
    override fun onCreate() {
        // Debug builds report disk access on the main thread, objects never closed and
        // leaked activities as they happen. They only log and flash the screen, never
        // crash: see ARCHITECTURE.md (Debug builds). Set before super.onCreate(), where
        // Hilt builds the dependency graph, so that's checked too.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen()
                    .build()
            )
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        }
        super.onCreate()
    }
}
