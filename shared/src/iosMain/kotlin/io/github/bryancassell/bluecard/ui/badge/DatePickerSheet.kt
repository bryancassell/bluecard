package io.github.bryancassell.bluecard.ui.badge

import io.github.bryancassell.bluecard.text.gregorianCalendar
import io.github.bryancassell.bluecard.text.toLocalDate
import io.github.bryancassell.bluecard.text.toNSDate
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.datetime.LocalDate
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
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
import platform.UIKit.UILayoutPriorityFittingSizeLevel
import platform.UIKit.UILayoutPriorityRequired
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
import platform.UIKit.systemBlueColor
import platform.darwin.NSObject

/**
 * iOS's own calendar, a [UIDatePicker] in its inline style, in a sheet from the bottom of the
 * window, with Cancel and Done in the sheet's bar, as iOS's own apps ask for a value in a sheet.
 * It starts at [initial], or at [today] if [initial] is later, and offers the days from 1900 to
 * [today], as Material 3's picker does on Android. Done gives [onConfirm] the day picked. Cancel,
 * or swiping the sheet down, calls [onCancel]. Neither closes the sheet: [close] does.
 *
 * Its days are in the Gregorian calendar, as the app's dates are, in [locale]'s language and
 * order, so the region's own calendar, such as Iran's Persian one, never shows. Material 3's
 * picker read a date typed into it in the region's calendar (#398). Each day's accessibility
 * label is still in the region's calendar, as "Sunday, Aban 10" for November 1, so screen
 * readers hear the day there. Giving [locale] the Gregorian calendar too didn't change it (#406).
 * They're in [timeZone], the app's today's: the picker marks the day it's in there as today.
 */
// Nothing UIKit keeps, such as a button's action or the sheet's height, refers back to this
// object, so they capture the constructor's parameters and locals, never a property. Kotlin/Native
// can't free a cycle that passes through an Objective-C object:
// https://kotlinlang.org/docs/native-arc-integration.html#retain-cycles
@OptIn(ExperimentalForeignApi::class)
internal class DatePickerSheet(
    initial: LocalDate,
    today: LocalDate,
    locale: NSLocale,
    timeZone: NSTimeZone,
    onConfirm: (LocalDate) -> Unit,
    onCancel: () -> Unit
) {
    val picker = UIDatePicker().apply {
        datePickerMode = UIDatePickerMode.UIDatePickerModeDate
        preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
        // The locale before the calendar, so the locale's own calendar can't replace it.
        this.locale = locale
        val gregorian = gregorianCalendar(timeZone)
        calendar = gregorian
        this.timeZone = timeZone
        minimumDate = FIRST_DAY.toNSDate(gregorian)
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
        primaryAction = picker.let { picker ->
            UIAction.actionWithHandler { onConfirm(picker.date.toLocalDate(picker.calendar)) }
        }
    )

    private val page = Page(picker).apply {
        navigationItem.leftBarButtonItem = cancelButton
        navigationItem.rightBarButtonItem = doneButton
    }

    private val dismissal = Dismissal(onCancel)

    val controller = UINavigationController(rootViewController = page).apply {
        view.tintColor = Tint
        presentationController?.delegate = dismissal
        sheetPresentationController?.detents = page.let { page ->
            listOf(
                UISheetPresentationControllerDetent.customDetentWithIdentifier(null) { context ->
                    minOf(page.contentHeight(), context!!.maximumDetentValue)
                }
            )
        }
    }

    private val onRefused = onCancel

    private var closed = false

    /**
     * Shows the sheet over [viewController]. If UIKit won't, as while another sheet appears, it's
     * as if the scout cancelled, so the page can ask again.
     */
    fun show(over: UIViewController) {
        over.presentViewController(controller, animated = true) {
            // Closed while it appeared, when UIKit ignores a dismissal.
            if (closed) controller.dismissViewControllerAnimated(true, completion = null)
        }
        if (controller.presentingViewController == null) onRefused()
    }

    /** Closes the sheet, once it has appeared if it's appearing. */
    fun close() {
        closed = true
        if (controller.presentingViewController != null &&
            !controller.isBeingPresented() &&
            !controller.isBeingDismissed()
        ) {
            controller.dismissViewControllerAnimated(true, completion = null)
        }
    }

    /**
     * The sheet's page, with [picker] in a scroll view, so it scrolls where the sheet is shorter
     * than it, as in landscape. The scroll view's content clears the sheet's bar by itself.
     */
    private class Page(private val picker: UIDatePicker) :
        UIViewController(nibName = null, bundle = null) {
        init {
            val scroll = UIScrollView()
            scroll.backgroundColor = UIColor.systemBackgroundColor
            scroll.addSubview(picker)
            NSLayoutConstraint.activateConstraints(
                listOf(
                    picker.topAnchor.constraintEqualToAnchor(scroll.contentLayoutGuide.topAnchor),
                    picker.bottomAnchor.constraintEqualToAnchor(
                        scroll.contentLayoutGuide.bottomAnchor
                    ),
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
        }

        private var laidOutHeight = 0.0

        /**
         * How tall the sheet is to be: the picker, at the width it's laid out at, and the page's
         * safe area above it. That's the bar, and on iOS 27 a margin of about 15 pt above it,
         * which counting the bar alone left out, so the last week ran into the sheet's edge.
         */
        fun contentHeight(): Double {
            val width = view.bounds.useContents { size.width } - 2 * SIDE_MARGIN
            // Before the sheet is first laid out, the page has no width yet.
            val size = if (width > 0) {
                picker.systemLayoutSizeFittingSize(
                    CGSizeMake(width, 0.0),
                    withHorizontalFittingPriority = UILayoutPriorityRequired,
                    verticalFittingPriority = UILayoutPriorityFittingSizeLevel
                )
            } else {
                picker.systemLayoutSizeFittingSize(UILayoutFittingCompressedSize.readValue())
            }
            return size.useContents { height } + view.safeAreaInsets.useContents { top }
        }

        // The sheet asks how tall to be before it's laid out, and again only when told to. So
        // once a layout changes the answer, as the first one does by placing the bar, and as
        // turning the phone or changing the text size can, it's told to.
        override fun viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            val height = contentHeight()
            if (height != laidOutHeight) {
                laidOutHeight = height
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

        /** The first day offered, as Material 3's picker's years start in 1900. */
        val FIRST_DAY = LocalDate(1900, 1, 1)

        /**
         * What iOS tints the picker's days and Done with: the app's primary color, or in dark
         * mode iOS's own blue. iOS draws white on it, under the selected day's number and as
         * Done's check from iOS 26, and on the dark scheme's pale primary that was 2.15:1, under
         * WCAG's 3:1 for large text. iOS 27's system blue, #0091FF, is 3.2:1 there, and 5.3:1 on
         * a dark sheet as text, as Done is on iOS 17 and 18.
         */
        val Tint = UIColor.colorWithDynamicProvider { traits ->
            if (traits?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) {
                UIColor.systemBlueColor
            } else {
                BlueCardLightColorScheme.primary.run {
                    UIColor.colorWithRed(
                        red.toDouble(),
                        green.toDouble(),
                        blue.toDouble(),
                        alpha.toDouble()
                    )
                }
            }
        }
    }
}
