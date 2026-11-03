package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.di.nsLog

/**
 * Until #348 and #349, iOS starts no other app: each link reports that no app can open it, and
 * the message about it goes to the log.
 */
@Composable
actual fun rememberOtherApps(): OtherApps = NoOtherApps

private object NoOtherApps : OtherApps {
    override fun openWebPage(url: String) = false

    override fun dial(phoneNumber: String) = false

    override fun email(address: String) = false

    override fun share(file: DocumentUri, mimeType: String) {}

    override fun showMessage(text: String) {
        nsLog(text)
    }
}
