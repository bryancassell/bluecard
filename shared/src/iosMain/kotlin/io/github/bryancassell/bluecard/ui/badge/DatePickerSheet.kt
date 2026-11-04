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
 * picker read a date typed into it in the region's calendar (#398).
 */
@OptIn(ExperimentalForeignApi::class)
internal class DatePickerSheet(
    initial: LocalDate,
    today: LocalDate,
    locale: NSLocale,
    private val onConfirm: (LocalDate) -> Unit,
    private val onCancel: () -> Unit
) {
    private val gregorian = gregorianCalendar()

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
    )

    private val page = UIViewController(nibName = null, bundle = null).apply {
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
            // As tall as the picker and the bar, or as the window allows.
            UISheetPresentationControllerDetent.customDetentWithIdentifier(null) { context ->
                val pickerHeight =
                    picker.systemLayoutSizeFittingSize(UILayoutFittingCompressedSize.readValue()).useContents {
                        height
                    }
                val barHeight = navigationBar.frame.useContents { size.height }
                minOf(pickerHeight + barHeight, context!!.maximumDetentValue)
            }
        )
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

        /** The app's primary color, which iOS tints the picker's days and Done with. */
        val Tint = UIColor.colorWithDynamicProvider { traits ->
            val dark = traits?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
            (if (dark) BlueCardDarkColorScheme else BlueCardLightColorScheme).primary.toUIColor()
        }

        fun Color.toUIColor() = UIColor.colorWithRed(
            red.toDouble(),
            green.toDouble(),
            blue.toDouble(),
            alpha.toDouble()
        )
    }
}
