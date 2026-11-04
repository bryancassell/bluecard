package io.github.bryancassell.bluecard.ui.badge

import androidx.compose.ui.graphics.Color
import io.github.bryancassell.bluecard.text.gregorianCalendar
import io.github.bryancassell.bluecard.text.toLocalDate
import io.github.bryancassell.bluecard.text.toNSDate
import io.github.bryancassell.bluecard.ui.theme.BlueCardLightColorScheme
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.datetime.LocalDate
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.timeZoneWithName
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.UIKit.resolvedColorWithTraitCollection
import platform.UIKit.systemBlueColor

// The sheet can't be shown here, with no app window, so these tap its buttons, and swipe it
// down, by calling what UIKit would.
@OptIn(ExperimentalForeignApi::class)
class DatePickerSheetTest {
    private val today = LocalDate(2026, 5, 20)
    private val events = mutableListOf<String>()

    // West of UTC, where the evening is already the next day in UTC.
    private val timeZone = assertNotNull(NSTimeZone.timeZoneWithName("America/Los_Angeles"))

    // The days as the app's calendar has them.
    private val calendar = gregorianCalendar(timeZone)

    private fun sheet(initial: LocalDate = LocalDate(2026, 5, 18), locale: String = "en_US") =
        DatePickerSheet(
            initial = initial,
            today = today,
            locale = NSLocale(locale),
            timeZone = timeZone,
            onConfirm = { events += "picked $it" },
            onCancel = { events += "cancelled" }
        )

    @Test
    fun picker_isIOSsInlineCalendar_ofDays() {
        val picker = sheet().picker

        assertEquals(UIDatePickerStyle.UIDatePickerStyleInline, picker.preferredDatePickerStyle)
        assertEquals(UIDatePickerMode.UIDatePickerModeDate, picker.datePickerMode)
    }

    @Test
    fun picker_startsAtTheInitialDate() {
        assertEquals(LocalDate(2026, 5, 18), sheet().picker.date.toLocalDate(calendar))
    }

    // As when the date was recorded while the device's clock was ahead.
    @Test
    fun picker_withAnInitialDateAfterToday_startsAtToday() {
        val picker = sheet(initial = LocalDate(2026, 6, 1)).picker

        assertEquals(today, picker.date.toLocalDate(calendar))
    }

    @Test
    fun picker_offersTheDaysFrom1900_toToday() {
        val picker = sheet().picker

        assertEquals(LocalDate(1900, 1, 1), assertNotNull(picker.minimumDate).toLocalDate(calendar))
        assertEquals(today, assertNotNull(picker.maximumDate).toLocalDate(calendar))
    }

    // The picker marks the day it's in there as today, which is the app's today. In UTC, it
    // marked tomorrow every evening west of UTC.
    @Test
    fun picker_isInTheTimeZoneGiven() {
        val picker = sheet().picker

        assertEquals(timeZone.name, picker.calendar.timeZone.name)
        assertEquals(timeZone.name, picker.timeZone?.name)
    }

    // Iran's calendar is the Persian one (#398).
    @Test
    fun picker_inALocaleWithAnotherCalendar_isInTheGregorianCalendar() {
        val picker = sheet(locale = "en_IR").picker

        assertEquals("en_IR", picker.locale?.localeIdentifier)
        assertEquals(NSCalendarIdentifierGregorian, picker.calendar.calendarIdentifier)
    }

    @Test
    fun done_givesTheDayPicked() {
        val sheet = sheet()
        sheet.picker.date = LocalDate(2026, 5, 2).toNSDate(calendar)

        sheet.doneButton.primaryAction!!.performWithSender(null, target = null)

        assertEquals(listOf("picked 2026-05-02"), events)
    }

    @Test
    fun cancel_givesNoDay() {
        val sheet = sheet()

        sheet.cancelButton.primaryAction!!.performWithSender(null, target = null)

        assertEquals(listOf("cancelled"), events)
    }

    @Test
    fun swipingTheSheetDown_cancels() {
        // Kept, since it holds the delegate, which the presentation holds only weakly.
        val sheet = sheet()
        val presentation = assertNotNull(sheet.controller.presentationController)
        val delegate = presentation.delegate as UIAdaptivePresentationControllerDelegateProtocol

        delegate.presentationControllerDidDismiss(presentation)

        assertEquals(listOf("cancelled"), events)
    }

    // The picker's height depends on its width: here, 341 pt at 375 pt wide, and 375 at 440. With
    // no window, the safe area above the picker, the bar on a phone, is 0 here.
    @Test
    fun contentHeight_isThePickersHeightAsLaidOut_andTheSafeAreaAboveIt() {
        for (width in listOf(375.0, 440.0)) {
            val sheet = sheet()
            val page = sheet.controller.viewControllers.single() as UIViewController
            sheet.controller.view.setFrame(CGRectMake(0.0, 0.0, width, 900.0))
            sheet.controller.view.layoutIfNeeded()

            val pickerHeight = sheet.picker.frame.useContents { size.height }
            val safeAreaTop = page.view.safeAreaInsets.useContents { top }
            assertEquals(pickerHeight + safeAreaTop, sheet.contentHeight(), 0.5, "$width")
        }
    }

    // As while another sheet appears. Here, UIKit refuses since the page has no window.
    @Test
    fun show_whenUIKitWont_cancels() {
        sheet().show(over = UIViewController())

        assertEquals(listOf("cancelled"), events)
    }

    @Test
    fun bar_hasCancelFirst_thenDone() {
        val sheet = sheet()
        val page = sheet.controller.viewControllers.single() as UIViewController

        // Equal, as Objective-C compares objects: Kotlin may wrap the same item twice.
        assertEquals(sheet.cancelButton, page.navigationItem.leftBarButtonItem)
        assertEquals(sheet.doneButton, page.navigationItem.rightBarButtonItem)
    }

    @Test
    fun tint_isTheAppsPrimaryColor_orInDarkModeIOSsBlue() {
        val tint = assertNotNull(sheet().controller.view.tintColor)

        assertColor(BlueCardLightColorScheme.primary, tint.resolved(Light))
        assertColor(UIColor.systemBlueColor.resolved(Dark), tint.resolved(Dark))
    }

    // Kotlin/Native can't free a cycle through Objective-C objects, so a button's action or the
    // sheet's height that refers back to the sheet would keep the sheet, its page's callbacks
    // with it, after it closes.
    @OptIn(NativeRuntimeApi::class, ExperimentalNativeApi::class)
    @Test
    fun sheet_onceNothingRefersToIt_isFreed() {
        val sheet = weakSheet()

        GC.collect()

        assertNull(sheet.value)
    }

    // Made in a function of its own, so no reference to the sheet stays on the test's stack.
    @OptIn(ExperimentalNativeApi::class)
    private fun weakSheet() = autoreleasepool { WeakReference(sheet()) }

    private fun UIColor.resolved(style: UIUserInterfaceStyle) = resolvedColorWithTraitCollection(
        UITraitCollection.traitCollectionWithUserInterfaceStyle(style)
    )

    private fun assertColor(expected: Color, color: UIColor) = memScoped {
        val red = alloc<CGFloatVar>()
        val green = alloc<CGFloatVar>()
        val blue = alloc<CGFloatVar>()
        assertTrue(color.getRed(red.ptr, green.ptr, blue.ptr, alpha = null))
        assertEquals(expected.red.toDouble(), red.value, 0.001)
        assertEquals(expected.green.toDouble(), green.value, 0.001)
        assertEquals(expected.blue.toDouble(), blue.value, 0.001)
    }

    private fun assertColor(expected: UIColor, color: UIColor) = memScoped {
        val red = alloc<CGFloatVar>()
        val green = alloc<CGFloatVar>()
        val blue = alloc<CGFloatVar>()
        assertTrue(expected.getRed(red.ptr, green.ptr, blue.ptr, alpha = null))
        assertColor(Color(red.value.toFloat(), green.value.toFloat(), blue.value.toFloat()), color)
    }

    private companion object {
        val Light = UIUserInterfaceStyle.UIUserInterfaceStyleLight
        val Dark = UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }
}
