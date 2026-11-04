package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.graphics.Color
import io.github.bryancassell.bluecard.text.gregorianCalendar
import io.github.bryancassell.bluecard.text.toLocalDate
import io.github.bryancassell.bluecard.text.toNSDate
import io.github.bryancassell.bluecard.ui.theme.BlueCardDarkColorScheme
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.datetime.LocalDate
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UILayoutFittingCompressedSize
import platform.UIKit.UINavigationController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIScrollView
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.colorWithDynamicProvider
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor
import platform.darwin.NSObject

/**
 * iOS's own calendar, a [UIDatePicker] in its inline style, in a sheet from the bottom of the
 * window, with Cancel and Done in the sheet's bar, as iOS's own apps ask for a value in a sheet.
 * It starts at [initial], or at [today] if [initial] is later, and offers no day after [today].
 * Done gives [onConfirm] the day picked. Cancel, or swiping the sheet down, calls [onCancel].
 * Neither closes the sheet: [close] does.
 *
 * Its days are in the Gregorian calendar, as the app's dates are, in [locale]'s language and
 * order, so the region's own calendar, such as Iran's Persian one, never shows. Material 3's
 * picker read a date typed into it in the region's calendar (#398). Each day's accessibility
 * label is still in the region's calendar, as "Sunday, Aban 10" for November 1, so screen
 * readers hear the day there. Giving [locale] the Gregorian calendar too didn't change it (#406).
 */
@OptIn(ExperimentalForeignApi::class)
internal class DatePickerSheet(
    initial: LocalDate,
    today: LocalDate,
    locale: NSLocale,
    private val onConfirm: (LocalDate) -> Unit,
    private val onCancel: () -> Unit
) {
    // In the device's time zone, as the app's today is: the picker marks the day it's in there
    // as today. In UTC, it marked tomorrow every evening west of UTC, as a day that can't be
    // picked.
    private val gregorian = gregorianCalendar(NSTimeZone.localTimeZone)

    val picker = UIDatePicker().apply {
        datePickerMode = UIDatePickerMode.UIDatePickerModeDate
        preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
        calendar = gregorian
        timeZone = gregorian.timeZone
        this.locale = locale
        maximumDate = today.toNSDate(gregorian)
        date = minOf(initial, today).toNSDate(gregorian)
        translatesAutoresizingMaskIntoConstraints = false
    }

    val cancelButton = UIBarButtonItem(
        barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
        primaryAction = UIAction.actionWithHandler { onCancel() }
    )

    val doneButton = UIBarButtonItem(
        barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemDone,
        primaryAction = UIAction.actionWithHandler { onConfirm(picker.date.toLocalDate(gregorian)) }
    ).apply { tintColor = DoneTint }

    private val page = Page().apply {
        // A scroll view's content clears the sheet's bar by itself. The picker scrolls where the
        // sheet is shorter than it, as in landscape.
        val scroll = UIScrollView()
        scroll.backgroundColor = UIColor.systemBackgroundColor
        scroll.addSubview(picker)
        NSLayoutConstraint.activateConstraints(
            listOf(
                picker.topAnchor.constraintEqualToAnchor(scroll.contentLayoutGuide.topAnchor),
                picker.bottomAnchor.constraintEqualToAnchor(scroll.contentLayoutGuide.bottomAnchor),
                picker.leadingAnchor.constraintEqualToAnchor(
                    scroll.frameLayoutGuide.leadingAnchor,
                    constant = SIDE_MARGIN
                ),
                picker.trailingAnchor.constraintEqualToAnchor(
                    scroll.frameLayoutGuide.trailingAnchor,
                    constant = -SIDE_MARGIN
                )
            )
        )
        view = scroll
        navigationItem.leftBarButtonItem = cancelButton
        navigationItem.rightBarButtonItem = doneButton
    }

    private val dismissal = Dismissal { onCancel() }

    val controller = UINavigationController(rootViewController = page).apply {
        view.tintColor = Tint
        presentationController?.delegate = dismissal
        sheetPresentationController?.detents = listOf(
            UISheetPresentationControllerDetent.customDetentWithIdentifier(null) { context ->
                minOf(contentHeight(), context!!.maximumDetentValue)
            }
        )
    }

    /**
     * The picker's height, and the page's safe area above it: the bar, and on iOS 27 the margin
     * above the bar, 15 pt on an iPhone 17. Counting the bar alone left the sheet that much
     * shorter than the picker, so its last week ran into the sheet's edge.
     */
    private fun contentHeight(): Double {
        val pickerHeight = picker.systemLayoutSizeFittingSize(
            UILayoutFittingCompressedSize.readValue()
        ).useContents { height }
        return pickerHeight + page.view.safeAreaInsets.useContents { top }
    }

    /** Shows the sheet over [viewController]. */
    fun show(over: UIViewController) {
        over.presentViewController(controller, animated = true, completion = null)
    }

    /** Closes the sheet, unless it's closed or closing. */
    fun close() {
        if (controller.presentingViewController != null && !controller.isBeingDismissed()) {
            controller.dismissViewControllerAnimated(true, completion = null)
        }
    }

    /**
     * The sheet's page, which has the sheet work out its height again when the top of the page's
     * safe area moves. The bar's place is known only once the sheet is laid out, after it first
     * asks how tall to be. (`viewSafeAreaInsetsDidChange` would say so directly, but it's declared
     * in a category, which Kotlin can't override.)
     */
    private class Page : UIViewController(nibName = null, bundle = null) {
        private var safeAreaTop = 0.0

        override fun viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            val top = view.safeAreaInsets.useContents { top }
            if (top != safeAreaTop) {
                safeAreaTop = top
                navigationController?.sheetPresentationController?.invalidateDetents()
            }
        }
    }

    /** Tells [onDismiss] when the scout swipes the sheet down. */
    private class Dismissal(private val onDismiss: () -> Unit) :
        NSObject(),
        UIAdaptivePresentationControllerDelegateProtocol {
        override fun presentationControllerDidDismiss(
            presentationController: UIPresentationController
        ) = onDismiss()
    }

    private companion object {
        /** The pages' side margin. */
        const val SIDE_MARGIN = 16.0

        /** The app's primary color, which iOS tints the picker's days with. */
        val Tint = dynamicColor(BlueCardLightColorScheme.primary, BlueCardDarkColorScheme.primary)

        /**
         * Done's color, which iOS fills it with under a white check. The dark scheme's primary is
         * too light for that, about 2:1, so in dark mode it's the primary container, as Material
         * fills a container under light content.
         */
        val DoneTint =
            dynamicColor(BlueCardLightColorScheme.primary, BlueCardDarkColorScheme.primaryContainer)

        fun dynamicColor(light: Color, dark: Color) = UIColor.colorWithDynamicProvider { traits ->
            val isDark = traits?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
            (if (isDark) dark else light).toUIColor()
        }

        fun Color.toUIColor() = UIColor.colorWithRed(
            red.toDouble(),
            green.toDouble(),
            blue.toDouble(),
            alpha.toDouble()
        )
    }
}
