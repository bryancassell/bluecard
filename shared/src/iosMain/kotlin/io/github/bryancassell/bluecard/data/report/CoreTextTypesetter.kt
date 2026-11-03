@file:OptIn(ExperimentalForeignApi::class)

package io.github.bryancassell.bluecard.data.report

import cnames.structs.__CFString
import io.github.bryancassell.bluecard.text.LocaleFormats
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFAttributedStringCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGAffineTransformIdentity
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetTextMatrix
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGPathCreateWithRect
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.CoreText.CTFontCreateUIFontForLanguage
import platform.CoreText.CTFrameDraw
import platform.CoreText.CTFrameGetLineOrigins
import platform.CoreText.CTFrameGetLines
import platform.CoreText.CTFrameGetVisibleStringRange
import platform.CoreText.CTFrameRef
import platform.CoreText.CTFramesetterCreateFrame
import platform.CoreText.CTFramesetterCreateWithAttributedString
import platform.CoreText.CTLineGetStringRange
import platform.CoreText.CTLineGetTypographicBounds
import platform.CoreText.CTLineRef
import platform.CoreText.CTParagraphStyleCreate
import platform.CoreText.CTParagraphStyleSetting
import platform.CoreText.CTTextAlignmentVar
import platform.CoreText.CTWritingDirectionVar
import platform.CoreText.kCTFontAttributeName
import platform.CoreText.kCTFontUIFontEmphasizedSystem
import platform.CoreText.kCTFontUIFontSystem
import platform.CoreText.kCTLanguageAttributeName
import platform.CoreText.kCTParagraphStyleAttributeName
import platform.CoreText.kCTParagraphStyleSpecifierAlignment
import platform.CoreText.kCTParagraphStyleSpecifierBaseWritingDirection
import platform.CoreText.kCTTextAlignmentCenter
import platform.CoreText.kCTTextAlignmentNatural
import platform.CoreText.kCTWritingDirectionLeftToRight
import platform.CoreText.kCTWritingDirectionRightToLeft
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString

/**
 * A report's text laid out by Core Text, in lines that start at [starts] in [text] and end at
 * [bottoms], in points down from the block's top.
 *
 * It keeps no Core Text objects: Kotlin doesn't release them, and Core Text's layout objects
 * belong to one thread, while a report is laid out and drawn wherever its coroutine runs. So
 * [draw] lays the text out again, the same way.
 */
class CoreTextText internal constructor(
    override val text: String,
    private val setting: TextSetting,
    private val starts: List<Int>,
    private val bottoms: List<Float>
) : ReportText {
    override val lineCount: Int get() = starts.size

    override fun lineStart(line: Int): Int = starts[line]

    override fun lineBottom(line: Int): Float = bottoms[line]

    /**
     * Draws the text onto [context], with its top left corner at ([x], [y]). [context] is
     * UIKit's, whose y grows downward, as UIGraphicsPDFRenderer's does.
     */
    fun draw(context: CGContextRef?, x: Float, y: Float) {
        withFrame(text, setting) { frame ->
            CGContextSaveGState(context)
            // Core Text draws with y growing upward, from the frame's bottom.
            CGContextTranslateCTM(context, x.toDouble(), y.toDouble())
            CGContextScaleCTM(context, 1.0, -1.0)
            CGContextTranslateCTM(context, 0.0, -FRAME_HEIGHT)
            CGContextSetTextMatrix(context, CGAffineTransformIdentity.readValue())
            CTFrameDraw(frame, context)
            CGContextRestoreGState(context)
        }
    }
}

/**
 * How a [CoreTextText] is set: in [style], up to [width] points wide, how it's aligned, and in
 * which [language].
 */
internal data class TextSetting(
    val style: ReportStyle,
    val width: Int,
    val centered: Boolean,
    val rightToLeft: Boolean,
    val language: String
)

/**
 * Lays a report's text out with Core Text in the system's font, with [formats] for the locale,
 * and lines that start at the right when [rightToLeft]. Text is set in [language], a BCP 47 tag
 * such as "en", which picks the glyphs of characters that languages draw differently, such as
 * Chinese and Japanese kanji, and how lines break, as StaticLayout's text locale does on Android.
 */
class CoreTextTypesetter(
    override val formats: LocaleFormats,
    override val rightToLeft: Boolean,
    private val language: String
) : ReportTypesetter<CoreTextText> {
    override fun layOut(
        text: String,
        style: ReportStyle,
        width: Int,
        centered: Boolean
    ): CoreTextText {
        val setting = TextSetting(style, width, centered, rightToLeft, language)
        // Core Text gives empty text no lines. It gets one empty line, as StaticLayout gives it
        // and ReportText asks, as high as a line of a space.
        if (text.isEmpty()) {
            val space = layOut(" ", style, width, centered)
            return CoreTextText(text, setting, listOf(0), listOf(space.lineBottom(0)))
        }
        val starts = mutableListOf<Int>()
        val bottoms = mutableListOf<Float>()
        withFrame(text, setting) { frame ->
            val shown = CTFrameGetVisibleStringRange(frame).useContents { length.toInt() }
            check(shown == text.length) {
                "Core Text left ${text.length - shown} characters out of its frame"
            }
            val lines = CTFrameGetLines(frame)
            val count = CFArrayGetCount(lines).toInt()
            memScoped {
                val origins = allocArray<CGPoint>(count)
                CTFrameGetLineOrigins(frame, CFRangeMake(0, 0), origins)
                val descent = alloc<CGFloatVar>()
                val leading = alloc<CGFloatVar>()
                for (index in 0 until count) {
                    val line: CTLineRef? = CFArrayGetValueAtIndex(lines, index.convert())
                        ?.reinterpret()
                    starts += CTLineGetStringRange(line).useContents { location.toInt() }
                    CTLineGetTypographicBounds(line, null, descent.ptr, leading.ptr)
                    // Origins are on each line's baseline, up from the frame's bottom.
                    val baseline = FRAME_HEIGHT - origins[index].y
                    bottoms += (baseline + descent.value + leading.value).toFloat()
                }
            }
        }
        return CoreTextText(text, setting, starts, bottoms)
    }
}

/**
 * The height of the frame text is laid out in. Core Text leaves out lines that don't fit, which
 * `layOut` checks for. A report's longest paragraph is a tracker row's values, each at most 2,000
 * characters (`TRACKER_MULTILINE_TEXT_MAX_LENGTH`, which imports are held to as well), so even
 * with a line for each character it takes a small part of this.
 */
private const val FRAME_HEIGHT = 1_000_000.0

/**
 * Lays [text] out as [setting] says, in a Core Text frame [TextSetting.width] points wide and
 * [FRAME_HEIGHT] high, and runs [block] with it. Every Core Text object is made and released on
 * the calling thread, as Core Text asks of its layout objects.
 */
private fun <R> withFrame(text: String, setting: TextSetting, block: (CTFrameRef?) -> R): R {
    val created = mutableListOf<CFTypeRef?>()
    fun <T : CPointer<*>> T?.releasedLater(): T? = also { created += it }
    try {
        val style = setting.style
        val language = cfString(setting.language).releasedLater()
        val fontType = if (style.bold) kCTFontUIFontEmphasizedSystem else kCTFontUIFontSystem
        val font =
            CTFontCreateUIFontForLanguage(fontType, style.size.toDouble(), language).releasedLater()
        val paragraphStyle = memScoped {
            val alignment = alloc<CTTextAlignmentVar>()
            alignment.value =
                if (setting.centered) kCTTextAlignmentCenter else kCTTextAlignmentNatural
            val direction = alloc<CTWritingDirectionVar>()
            direction.value = if (setting.rightToLeft) {
                kCTWritingDirectionRightToLeft
            } else {
                kCTWritingDirectionLeftToRight
            }
            val settings = allocArray<CTParagraphStyleSetting>(2)
            settings[0].spec = kCTParagraphStyleSpecifierAlignment
            settings[0].valueSize = sizeOf<CTTextAlignmentVar>().convert()
            settings[0].value = alignment.ptr
            settings[1].spec = kCTParagraphStyleSpecifierBaseWritingDirection
            settings[1].valueSize = sizeOf<CTWritingDirectionVar>().convert()
            settings[1].value = direction.ptr
            CTParagraphStyleCreate(settings, 2.convert())
        }.releasedLater()
        val attributes = CFDictionaryCreateMutable(
            null,
            3,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ).releasedLater()
        CFDictionarySetValue(attributes, kCTFontAttributeName, font)
        CFDictionarySetValue(attributes, kCTParagraphStyleAttributeName, paragraphStyle)
        CFDictionarySetValue(attributes, kCTLanguageAttributeName, language)
        // A CFString of the same UTF-16 code units, so a line's position in it is its index in
        // text.
        val string = cfString(text).releasedLater()
        val attributed = CFAttributedStringCreate(null, string, attributes).releasedLater()
        val framesetter = CTFramesetterCreateWithAttributedString(attributed).releasedLater()
        val bounds = CGRectMake(0.0, 0.0, setting.width.toDouble(), FRAME_HEIGHT)
        val path = CGPathCreateWithRect(bounds, null).releasedLater()
        val frame =
            CTFramesetterCreateFrame(framesetter, CFRangeMake(0, 0), path, null).releasedLater()
        return block(frame)
    } finally {
        created.asReversed().forEach { it?.let(::CFRelease) }
    }
}

/** [text] as a CFString that the caller releases. */
private fun cfString(text: String): CFStringRef? =
    CFBridgingRetain(text as NSString)?.reinterpret<__CFString>()
