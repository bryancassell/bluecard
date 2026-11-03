// Draws the iOS app's icons from the Android launcher icon's shapes
// (app/src/main/res/drawable/ic_launcher_foreground.xml): a white card on the background color,
// with two lines in that color, blue for test builds and orange for debug builds, as on Android.
// iOS icons are 1024 px squares that iOS rounds itself, so they show the 72 dp in the middle of
// the 108 dp drawing, which Android's launchers show. Run it from the repository root after
// changing the Android icon:
//
//     swift scripts/draw-ios-app-icons.swift
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let size = 1024
let scale = CGFloat(size) / 72
let inset: CGFloat = 18

/// A rectangle of the Android drawing, in dp, as pixels of the icon.
func pixels(x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat) -> CGRect {
    CGRect(x: (x - inset) * scale, y: (y - inset) * scale, width: width * scale, height: height * scale)
}

func srgb(_ rgb: UInt32) -> CGColor {
    CGColor(
        srgbRed: CGFloat((rgb >> 16) & 0xFF) / 255,
        green: CGFloat((rgb >> 8) & 0xFF) / 255,
        blue: CGFloat(rgb & 0xFF) / 255,
        alpha: 1
    )
}

func drawIcon(background: UInt32, path: String) {
    // No alpha channel, which App Store Connect refuses in an app icon.
    let context = CGContext(
        data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!,
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    )!
    // Down the page, as in Android's drawing.
    context.translateBy(x: 0, y: CGFloat(size))
    context.scaleBy(x: 1, y: -1)

    context.setFillColor(srgb(background))
    context.fill(CGRect(x: 0, y: 0, width: size, height: size))
    context.setFillColor(srgb(0xFFFFFF))
    context.addPath(CGPath(
        roundedRect: pixels(x: 32, y: 38, width: 44, height: 32),
        cornerWidth: 4 * scale, cornerHeight: 4 * scale, transform: nil
    ))
    context.fillPath()
    context.setFillColor(srgb(background))
    context.fill(pixels(x: 40, y: 46, width: 20, height: 4))
    context.fill(pixels(x: 40, y: 54, width: 28, height: 4))

    let url = URL(fileURLWithPath: path)
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, context.makeImage()!, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("Couldn't write \(path)") }
}

let icons = "iosApp/iosApp/Assets.xcassets"
// ic_launcher_background in app/src/main/res and app/src/debug/res.
drawIcon(background: 0x003F87, path: "\(icons)/AppIcon.appiconset/AppIcon.png")
drawIcon(background: 0xBF360C, path: "\(icons)/AppIconDebug.appiconset/AppIconDebug.png")
