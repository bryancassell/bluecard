package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Returns the [OtherAppStarter] a screen shares among its controls that open another app, such
 * as a link, or Save report and Share report, which open the file picker and the share sheet.
 * It ignores taps that follow too quickly.
 */
@Composable
fun rememberOtherAppStarter(): OtherAppStarter {
    val otherApps = rememberOtherApps()
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val scope = rememberCoroutineScope()
    return remember(otherApps, timeoutMillis, scope) {
        OtherAppStarter(otherApps, timeoutMillis, scope)
    }
}

/**
 * Starts another app, such as the browser to open a link, or shows a message if no app on the
 * phone can handle it. Made with [rememberOtherAppStarter].
 *
 * After each start, it ignores further starts for the double-tap timeout
 * (`ViewConfiguration.doubleTapTimeoutMillis`). The other app takes a moment to cover
 * BlueCard, so otherwise both taps of a double tap could start it, and a browser could open
 * two tabs, or an email app two drafts (#97). A screen passes the one starter to each of its
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
    private val otherApps: OtherApps,
    private val timeoutMillis: Long,
    private val scope: CoroutineScope
) {
    private var ignoring = false

    /** Opens the web page at [url] in the browser, or shows [noApp] if no app on the phone can. */
    fun openWebPage(url: String, noApp: String) {
        start(noApp) { otherApps.openWebPage(url) }
    }

    /** Opens the phone app with [phoneNumber], or shows [noApp] if no app on the phone can. */
    fun dial(phoneNumber: String, noApp: String) {
        start(noApp) { otherApps.dial(phoneNumber) }
    }

    /** Opens an email app to write to [address], or shows [noApp] if no app on the phone can. */
    fun email(address: String, noApp: String) {
        start(noApp) { otherApps.email(address) }
    }

    /**
     * Opens [picker] with [input], such as the system file picker with the name of the file to
     * create, or shows [noApp] if no app on the phone can handle it.
     */
    fun <I> launch(picker: DocumentPicker<I>, input: I, noApp: String) {
        start(noApp) { picker.launch(input) }
    }

    /**
     * Runs [action] for a tap that opens another app once something is ready, such as Share
     * report, which opens the share sheet once the report is created, or for a tap on a control
     * beside ones that open another app, such as Clear all, whose dialog shouldn't open under
     * the other app. It's ignored, and ignores other taps, as a start is.
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

    /** Runs [start], which says whether an app started, as a tap, showing [noApp] if none did. */
    private fun start(noApp: String, start: () -> Boolean) {
        tap { if (!start()) otherApps.showMessage(noApp) }
    }
}
