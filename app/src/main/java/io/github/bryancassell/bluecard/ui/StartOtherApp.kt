package io.github.bryancassell.bluecard.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Returns a function that starts another app with an intent, such as the browser to open a
 * link, or shows noApp if no app on the phone can handle it.
 *
 * Once it has started an app, it ignores further calls until BlueCard's window has focus
 * again, as when the scout comes back from that app. The other app takes a moment to cover
 * BlueCard, so otherwise both taps of a double tap could start it, and a browser could open
 * two tabs, or an email app two drafts. A screen passes the one function to each of its
 * controls that opens another app, so a quick tap on a second one is ignored too. A call that
 * finds no app doesn't count, so the next one tries again.
 *
 * It waits for window focus rather than for the activity to resume: in desktop windows, the
 * other app opens in a window of its own, and BlueCard stays resumed beside it.
 */
@Composable
fun rememberStartOtherApp(): (intent: Intent, noApp: String) -> Unit {
    val context = LocalContext.current
    var started by remember { mutableStateOf(false) }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (focused) started = false
    }
    return remember(context) {
        { intent, noApp ->
            if (!started) {
                try {
                    context.startActivity(intent)
                    started = true
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
