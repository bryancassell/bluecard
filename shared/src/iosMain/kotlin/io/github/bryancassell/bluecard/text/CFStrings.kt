@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.text

import cnames.structs.__CFString
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFStringRef
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString

/**
 * This string as a CFString of the same UTF-16 code units, so an index in one is the same in the
 * other. The caller releases it with `CFRelease`.
 */
@Suppress("CAST_NEVER_SUCCEEDS") // A Kotlin string is an NSString on iOS.
internal fun String.toCFString(): CFStringRef =
    CFBridgingRetain(this as NSString)!!.reinterpret<__CFString>()
