package io.github.bryancassell.bluecard

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Starts Hilt's dependency graph for the whole app. */
@HiltAndroidApp
class BlueCardApplication : Application()
