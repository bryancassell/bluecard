package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.di.nsLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

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
internal class IosOtherApps(private val viewController: UIViewController) : OtherApps {
    override fun openWebPage(url: String) = false

    override fun dial(phoneNumber: String) = false

    override fun email(address: String) = false

    // The share sheet tells the file's type from its name's extension, so it needs no MIME type.
    @OptIn(ExperimentalForeignApi::class)
    override fun share(file: DocumentUri, mimeType: String) {
        // Over whatever shows, such as the file picker if the scout tapped Save report while the
        // report was being made: a view controller that's showing another can't show the share
        // sheet, which would be lost. It's still lost if the report is ready while the picker is
        // still appearing, which took up to 3.3 s for the first picker after launch, or closing:
        // UIKit lists a picker as presented only once it has appeared, refuses to present during
        // a transition, and says so only in the log. The scout can tap Share report again.
        var presenter = viewController
        while (true) presenter = presenter.presentedViewController ?: break
        val shareSheet = UIActivityViewController(
            activityItems = listOf(file.url),
            applicationActivities = null
        )
        // Where the share sheet is a popover, as it is in a regular-width window, UIKit throws
        // unless it's anchored. It points at nothing, from the middle of the page.
        shareSheet.popoverPresentationController?.let { popover ->
            val view = presenter.view
            popover.sourceView = view
            popover.sourceRect = view.bounds.useContents {
                CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
            }
            popover.permittedArrowDirections = 0u
        }
        presenter.presentViewController(shareSheet, animated = true, completion = null)
    }

    override fun showMessage(text: String) {
        nsLog(text)
    }
}
