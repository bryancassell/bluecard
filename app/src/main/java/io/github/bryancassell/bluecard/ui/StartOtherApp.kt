package io.github.bryancassell.bluecard.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Returns a function that starts another app with an intent, such as the browser to open a
 * link, or shows noApp if no app on the phone can handle it.
 *
 * After each call, it ignores further calls for the double-tap timeout
 * (`ViewConfiguration.doubleTapTimeoutMillis`). The other app takes a moment to cover
 * BlueCard, so otherwise both taps of a double tap could start it, and a browser could open
 * two tabs, or an email app two drafts. A screen passes the one function to each of its
 * controls that opens another app, so a quick tap on a second one is ignored too. A call that
 * finds no app counts too, so a double tap shows its message once, and the next tap tries
 * again.
 *
 * It doesn't wait for the scout to come back from the other app: some starts never take
 * BlueCard's place, such as one that screen pinning blocks, which doesn't throw, and the
 * controls would stay locked.
 */
@Composable
fun rememberStartOtherApp(): (intent: Intent, noApp: String) -> Unit {
    val context = LocalContext.current
    val timeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val scope = rememberCoroutineScope()
    return remember(context, timeoutMillis, scope) {
        var ignoring = false
        fun(intent: Intent, noApp: String) {
            if (ignoring) return
            ignoring = true
            scope.launch {
                delay(timeoutMillis)
                ignoring = false
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
