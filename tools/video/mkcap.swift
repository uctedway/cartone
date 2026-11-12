import AppKit
// 자막 띠 그림 만들기: swift mkcap.swift 출력.png "제목" "부제" "아래 안내"
let a = CommandLine.arguments
let W = 1920, H = 180
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: W, pixelsHigh: H, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSColor(calibratedRed: 0.07, green: 0.08, blue: 0.10, alpha: 1).setFill()
NSRect(x: 0, y: 0, width: W, height: H).fill()
func font(_ n: String, _ s: CGFloat) -> NSFont { NSFont(name: n, size: s) ?? NSFont.systemFont(ofSize: s) }
let title: [NSAttributedString.Key: Any] = [.font: font("AppleSDGothicNeo-Bold", 46), .foregroundColor: NSColor.white]
let sub: [NSAttributedString.Key: Any] = [.font: font("AppleSDGothicNeo-Regular", 31), .foregroundColor: NSColor(calibratedWhite: 0.80, alpha: 1)]
let foot: [NSAttributedString.Key: Any] = [.font: font("AppleSDGothicNeo-Regular", 22), .foregroundColor: NSColor(calibratedRed: 0.95, green: 0.72, blue: 0.35, alpha: 1)]
(a[2] as NSString).draw(at: NSPoint(x: 60, y: 96), withAttributes: title)
(a[3] as NSString).draw(at: NSPoint(x: 60, y: 50), withAttributes: sub)
(a[4] as NSString).draw(at: NSPoint(x: 60, y: 12), withAttributes: foot)
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: a[1]))
