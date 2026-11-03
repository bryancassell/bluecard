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
 * Where drawing darkened a white page, in pixels from its top left corner, and how dark it made it
 * altogether. [found] is false when nothing was drawn.
 */
internal class Ink(val left: Int, val right: Int, val top: Int, val bottom: Int, val amount: Int) {
    val found: Boolean get() = right >= left
}

/**
 * Runs [draw] on a white, page-sized gray bitmap, one pixel a point, and finds where it's dark.
 * With [uiKitCoordinates], y grows downward, as in UIGraphicsPDFRenderer's context; otherwise
 * it grows upward from the bottom, as in a PDF page's.
 */
internal fun ink(uiKitCoordinates: Boolean = true, draw: (CGContextRef?) -> Unit): Ink {
    val width = PAGE_WIDTH
    val height = PAGE_HEIGHT
    val gray = CGColorSpaceCreateDeviceGray()
    val context = CGBitmapContextCreate(
        null,
        width.toULong(),
        height.toULong(),
        8u,
        0u,
        gray,
        CGImageAlphaInfo.kCGImageAlphaNone.value
    )
    try {
        CGContextSetGrayFillColor(context, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        if (uiKitCoordinates) {
            CGContextTranslateCTM(context, 0.0, height.toDouble())
            CGContextScaleCTM(context, 1.0, -1.0)
        }
        draw(context)
        // The bitmap's first row is the page's top.
        val pixels = CGBitmapContextGetData(context)!!.reinterpret<UByteVar>()
        val rowBytes = CGBitmapContextGetBytesPerRow(context).toInt()
        var left = width
        var right = -1
        var top = height
        var bottom = -1
        var amount = 0
        for (row in 0 until height) {
            for (column in 0 until width) {
                val darkness = 255 - pixels[row * rowBytes + column].toInt()
                if (darkness > 128) {
                    left = minOf(left, column)
                    right = maxOf(right, column)
                    top = minOf(top, row)
                    bottom = maxOf(bottom, row)
                }
                amount += darkness
            }
        }
        return Ink(left, right, top, bottom, amount)
    } finally {
        CGContextRelease(context)
        CGColorSpaceRelease(gray)
    }
}
