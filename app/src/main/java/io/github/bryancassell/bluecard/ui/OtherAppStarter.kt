package io.github.bryancassell.bluecard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Returns an [OtherAppStarter] for a screen, which starts other apps, such as the browser to
 * open a link, and ignores taps that follow too quickly.
 */
@Composable
fun rememberStartOtherApp(): OtherAppStarter {
    val context = LocalContext.current
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val scope = rememberCoroutineScope()
    return remember(context, timeoutMillis, scope) {
        OtherAppStarter(context, timeoutMillis, scope)
    }
}

/**
 * Starts another app, such as the browser to open a link, or shows a message if no app on the
 * phone can handle it. Made with [rememberStartOtherApp].
 *
 * After each start, it ignores further starts for the double-tap timeout
 * (`ViewConfiguration.doubleTapTimeoutMillis`). The other app takes a moment to cover
 * BlueCard, so otherwise both taps of a double tap could start it, and a browser could open
 * two tabs, or an email app two drafts. A screen passes the one starter to each of its
 * controls that opens another app, so a quick tap on a second one is ignored too. A start that
 * finds no app counts too, so a double tap shows its message once, and the next tap tries
 * again.
 *
 * It doesn't wait for the scout to come back from the other app, such as for `ON_RESUME` or for
 * BlueCard's window to get focus back: some starts never take BlueCard's place, such as one that
 * screen pinning blocks, which doesn't throw, and the controls would stay locked. Waiting for
 * focus would also misfire in desktop windows, where the tap that focuses BlueCard's window can
 * arrive before the focus does. So a tap after the timeout can still reach BlueCard if the other
 * app hasn't covered it yet: on an Android 17 (API 37) emulator, Android dropped most second
 * taps that came 140–200 ms after the first, as the other app took over, but not all of them.
 */
class OtherAppStarter internal constructor(
    private val context: Context,
    private val timeoutMillis: Long,
    private val scope: CoroutineScope
) {
    private var ignoring = false

    /** Starts another app with [intent], or shows [noApp] if no app on the phone can. */
    operator fun invoke(intent: Intent, noApp: String) {
        tap { start(noApp) { context.startActivity(intent) } }
    }

    /**
     * Launches [launcher] with [input], such as the system file picker with the name of the
     * file to create, or shows [noApp] if no app on the phone can handle it.
     */
    fun <I> launch(launcher: ActivityResultLauncher<I>, input: I, noApp: String) {
        tap { start(noApp) { launcher.launch(input) } }
    }

    /**
     * Runs [action] for a tap that opens another app once something is ready, such as Share
     * report, which opens the share sheet once the report is created. It's ignored, and ignores
     * other taps, as a start is.
     */
    fun tap(action: () -> Unit) {
        if (ignoring) return
        ignoring = true
        scope.launch {
            delay(timeoutMillis)
            ignoring = false
        }
        action()
    }

    private fun start(noApp: String, start: () -> Unit) {
        try {
            start()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
        }
    }
}
