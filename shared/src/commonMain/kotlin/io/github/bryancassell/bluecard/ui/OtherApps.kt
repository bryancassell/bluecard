package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.data.DocumentUri

/**
 * The other apps on the phone, which a screen starts through its [OtherAppStarter], from
 * [rememberOtherApps]. Each start returns whether an app started: false when no app on the phone
 * can handle it, as when parental controls block the browser.
 */
interface OtherApps {
    /** Opens the web page at [url] in the browser. */
    fun openWebPage(url: String): Boolean

    /** Opens the phone app with [phoneNumber] filled in, without calling it. */
    fun dial(phoneNumber: String): Boolean

    /** Opens an email app to write to [address]. */
    fun email(address: String): Boolean

    /** Opens the share sheet, to send [file], of [mimeType], to the app the scout picks. */
    fun share(file: DocumentUri, mimeType: String)

    /** Shows [text] briefly over the screen, such as that no app could open a link. */
    fun showMessage(text: String)
}

/** The phone's [OtherApps], started from the screen that calls this. */
@Composable
expect fun rememberOtherApps(): OtherApps
