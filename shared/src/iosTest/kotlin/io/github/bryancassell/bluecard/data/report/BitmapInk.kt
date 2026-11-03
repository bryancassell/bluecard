@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetBytesPerRow
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetGrayFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake

/**
 * Runs [draw] on a white, page-sized gray bitmap, one pixel a point, and finds where it's dark.
 * With [uiKitCoordinates], y grows downward, as in UIGraphicsPDFRenderer's context; otherwise
 * it grows upward from the bottom, as in a PDF page's.
 */
internal fun bitmapInk(uiKitCoordinates: Boolean = true, draw: (CGContextRef?) -> Unit): Ink {
    val gray = CGColorSpaceCreateDeviceGray()
    val context = CGBitmapContextCreate(
        null,
        PAGE_WIDTH.toULong(),
        PAGE_HEIGHT.toULong(),
        8u,
        0u,
        gray,
        CGImageAlphaInfo.kCGImageAlphaNone.value
    )
    try {
        CGContextSetGrayFillColor(context, 1.0, 1.0)
        val page = CGRectMake(0.0, 0.0, PAGE_WIDTH.toDouble(), PAGE_HEIGHT.toDouble())
        CGContextFillRect(context, page)
        if (uiKitCoordinates) {
            CGContextTranslateCTM(context, 0.0, PAGE_HEIGHT.toDouble())
            CGContextScaleCTM(context, 1.0, -1.0)
        }
        draw(context)
        // The bitmap's first row is the page's top.
        val pixels = CGBitmapContextGetData(context)!!.reinterpret<UByteVar>()
        val rowBytes = CGBitmapContextGetBytesPerRow(context).toInt()
        return Ink.of(PAGE_WIDTH, PAGE_HEIGHT) { column, row ->
            255 - pixels[row * rowBytes + column].toInt()
        }
    } finally {
        CGContextRelease(context)
        CGColorSpaceRelease(gray)
    }
}
