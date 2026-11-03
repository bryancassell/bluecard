package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.di.nsLog
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController

/** Shows the share sheet over the app's view controller. */
@Composable
actual fun rememberOtherApps(): OtherApps {
    val viewController = LocalUIViewController.current
    return remember(viewController) { IosOtherApps(viewController) }
}

/**
 * Shares files with the share sheet, over [viewController]. Until #349, iOS opens no link: each
 * reports that no app can open it, and the message about it goes to the log.
 */
private class IosOtherApps(private val viewController: UIViewController) : OtherApps {
    override fun openWebPage(url: String) = false

    override fun dial(phoneNumber: String) = false

    override fun email(address: String) = false

    // The share sheet tells the file's type from its name's extension, so it needs no MIME type.
    override fun share(file: DocumentUri, mimeType: String) {
        val shareSheet = UIActivityViewController(
            activityItems = listOf(file.url),
            applicationActivities = null
        )
        viewController.presentViewController(shareSheet, animated = true, completion = null)
    }

    override fun showMessage(text: String) {
        nsLog(text)
    }
}
