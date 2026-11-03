package io.github.bryancassell.bluecard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import io.github.bryancassell.bluecard.data.DocumentUri
import io.github.bryancassell.bluecard.resources.Res
import io.github.bryancassell.bluecard.resources.no_app_ok
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/** Opens links with the system, and shows the share sheet and messages over the app's view. */
@Composable
actual fun rememberOtherApps(): OtherApps {
    val viewController = LocalUIViewController.current
    val ok = stringResource(Res.string.no_app_ok)
    return remember(viewController, ok) { IosOtherApps(viewController, ok, SystemUrlOpener) }
}

/**
 * Opens links with [urls], and shows the share sheet and messages over [viewController]. A
 * message is an alert closed with [ok]. iOS has no brief message like Android's toast, and the
 * scout needs to know their tap did nothing, which VoiceOver reads an alert for when it appears.
 */
internal class IosOtherApps(
    private val viewController: UIViewController,
    private val ok: String,
    private val urls: UrlOpener
) : OtherApps {
    override fun openWebPage(url: String) = open(NSURL.URLWithString(url))

    // iOS asks the scout to call or cancel, so nothing calls without their say, as Android's
    // ACTION_DIAL only fills the number in. It's dialed as typed, without spaces, which RFC 3966
    // doesn't allow in a tel: URL: https://www.rfc-editor.org/rfc/rfc3966#section-3
    override fun dial(phoneNumber: String) =
        open(url("tel", phoneNumber.filterNot { it.isWhitespace() }, keep = "+"))

    // Keeps @ and + as Android's email() does, since some apps show the address as the link
    // spells it.
    override fun email(address: String) = open(url("mailto", address, keep = "@+"))

    // The share sheet tells the file's type from its name's extension, so it needs no MIME type.
    @OptIn(ExperimentalForeignApi::class)
    override fun share(file: DocumentUri, mimeType: String) {
        val presenter = topViewController()
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

    // The text is the title, as Apple's guidelines ask of a sentence that says what happened:
    // https://developer.apple.com/design/human-interface-guidelines/alerts
    override fun showMessage(text: String) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = text,
            message = null,
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(ok, style = UIAlertActionStyleDefault, handler = null)
        )
        topViewController().presentViewController(alert, animated = true, completion = null)
    }

    /**
     * Whatever shows, such as the file picker if the scout tapped Save report while the report
     * was being made: a view controller that's showing another can't show one more, which would
     * be lost. It's still lost if it's ready while the picker is still appearing, which took up
     * to 3.3 s for the first picker after launch, or closing: UIKit lists a picker as presented
     * only once it has appeared, refuses to present during a transition, and says so only in the
     * log. The scout can tap again.
     */
    private fun topViewController(): UIViewController {
        var presenter = viewController
        while (true) presenter = presenter.presentedViewController ?: break
        return presenter
    }

    // canOpenURL is how Apple has an app find out whether any app can open a link, and after
    // it's true, it guarantees openURL opens it:
    // https://developer.apple.com/documentation/uikit/uiapplication/canopenurl(_:)
    private fun open(url: NSURL?): Boolean {
        if (url == null || !urls.canOpen(url)) return false
        urls.open(url)
        return true
    }
}

/**
 * The URL with [scheme] for [text], which is percent-encoded but for [keep] and what Android's
 * `Uri.encode` keeps, or null if it can't be, as for text with half an emoji.
 */
@OptIn(BetaInteropApi::class)
internal fun url(scheme: String, text: String, keep: String): NSURL? {
    val allowed = NSCharacterSet.characterSetWithCharactersInString(UNRESERVED + keep)
    val encoded = NSString.create(string = text)
        .stringByAddingPercentEncodingWithAllowedCharacters(allowed) ?: return null
    return NSURL.URLWithString("$scheme:$encoded")
}

// The characters Android's Uri.encode leaves as they are.
private const val UNRESERVED =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*"

/** Opens links with other apps, which tests replace. */
internal interface UrlOpener {
    /** Whether an app on the phone can open [url]. */
    fun canOpen(url: NSURL): Boolean

    /** Opens [url] in the app that handles it. */
    fun open(url: NSURL)
}

/**
 * Opens links with UIKit. Its canOpenURL answers only for the schemes Info.plist lists in
 * `LSApplicationQueriesSchemes`: https, tel and mailto.
 */
internal object SystemUrlOpener : UrlOpener {
    override fun canOpen(url: NSURL) = UIApplication.sharedApplication.canOpenURL(url)

    override fun open(url: NSURL) {
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), null)
    }
}
