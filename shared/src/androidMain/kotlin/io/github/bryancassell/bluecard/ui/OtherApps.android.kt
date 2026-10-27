package io.github.bryancassell.bluecard.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.github.bryancassell.bluecard.data.DocumentUri

/**
 * Starts other apps with the screen's activity, so each opens over BlueCard, in its task. From the
 * app's context, each would need a task of its own.
 */
@Composable
actual fun rememberOtherApps(): OtherApps {
    val context = LocalContext.current
    return remember(context) { AndroidOtherApps(context) }
}

/** Starts other apps with intents from [context], and shows messages as toasts. */
internal class AndroidOtherApps(private val context: Context) : OtherApps {
    // ACTION_VIEW, as Compose's UriHandler uses.
    override fun openWebPage(url: String) = start(Intent(Intent.ACTION_VIEW, url.toUri()))

    // ACTION_DIAL fills in the number without calling it, so it needs no permission. It gets the
    // number as typed, which is how it's stored.
    override fun dial(phoneNumber: String) =
        start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)))

    // Only email apps handle ACTION_SENDTO with a mailto: address. It keeps @ and + as they are,
    // since some apps show the address as the link spells it, and encodes anything that would
    // change the link, such as ? or #.
    override fun email(address: String) =
        start(Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(address, "@+")}".toUri()))

    // The share sheet is always there to start. The clip data lets it show the file, and the flag
    // grants the app picked permission to read it:
    // https://developer.android.com/training/sharing/send#adding-rich-content-previews
    override fun share(file: DocumentUri, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, file)
            clipData = ClipData.newRawUri(null, file)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    override fun showMessage(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun start(intent: Intent): Boolean = startsApp { context.startActivity(intent) }
}

/** Runs [start], which starts another app, and returns false if no app on the phone can. */
internal inline fun startsApp(start: () -> Unit): Boolean = try {
    start()
    true
} catch (_: ActivityNotFoundException) {
    false
}
