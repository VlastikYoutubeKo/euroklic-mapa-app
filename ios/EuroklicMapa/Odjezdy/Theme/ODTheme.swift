import SwiftUI
import UIKit

/// Barevné tokeny nové verze webu (`public/v2/v2.css` + `palettes.css`).
/// Čísla jsou ve stejném pořadí jako v CSS (bg, surface, surface-2, text, ink, ink-text, muted, line,
/// accent, sign, sign-text, mark, bad, good).
struct ODTokens {
    let bg: UInt32
    let surface: UInt32
    let surface2: UInt32
    let text: UInt32
    let ink: UInt32
    let inkText: UInt32
    let muted: UInt32
    let line: UInt32
    let accent: UInt32
    let sign: UInt32
    let signText: UInt32
    let mark: UInt32
    let bad: UInt32
    let good: UInt32

    init(_ bg: UInt32, _ surface: UInt32, _ surface2: UInt32, _ text: UInt32, _ ink: UInt32,
         _ inkText: UInt32, _ muted: UInt32, _ line: UInt32, _ accent: UInt32, _ sign: UInt32,
         _ signText: UInt32, _ mark: UInt32, _ bad: UInt32, _ good: UInt32) {
        self.bg = bg
        self.surface = surface
        self.surface2 = surface2
        self.text = text
        self.ink = ink
        self.inkText = inkText
        self.muted = muted
        self.line = line
        self.accent = accent
        self.sign = sign
        self.signText = signText
        self.mark = mark
        self.bad = bad
        self.good = good
    }
}

struct ODPalette: Identifiable {
    let id: String
    let name: String
    let light: ODTokens
    let dark: ODTokens

    static let defaultId = ""

    static let all: [ODPalette] = [
        ODPalette(
            id: "", name: L("Označník (výchozí)"),
            light: ODTokens(0xe9ebee, 0xffffff, 0xf1f2f4, 0x16181d, 0x16181d, 0xffffff, 0x5c6470, 0xdfe2e7, 0x1b4fa8, 0xc8102e, 0xffffff, 0xffd400, 0xc4161c, 0x0b7a4b),
            dark: ODTokens(0x101216, 0x17191d, 0x21252b, 0xf1f2f4, 0xf1f2f4, 0x14171b, 0x9aa3b0, 0x2e333b, 0x7aa7ff, 0xc8102e, 0xffffff, 0xffd400, 0xff6b6f, 0x4cd08a)
        ),
        ODPalette(
            id: "modra", name: L("Modrá a bronz"),
            light: ODTokens(0xdee5e3, 0xfffaff, 0xf0f0f2, 0x000501, 0x000501, 0xfffaff, 0x616262, 0xd9d5d9, 0x2071a7, 0x2176ae, 0xffffff, 0xbb9f06, 0xc4161c, 0x0b7a4b),
            dark: ODTokens(0x000401, 0x121613, 0x262a27, 0xfffaff, 0xfffaff, 0x121613, 0xa09fa1, 0x3a3d3b, 0x5696c1, 0x2176ae, 0xffffff, 0xbb9f06, 0xff6b6f, 0x4cd08a)
        ),
        ODPalette(
            id: "limetka", name: L("Limetka a oheň"),
            light: ODTokens(0xeeefdb, 0xf7f7f2, 0xf3f3e7, 0x261c15, 0x261c15, 0xf7f7f2, 0x726c66, 0xd8d6d1, 0xbb4c1f, 0xf05d23, 0x261c15, 0xc5d86d, 0xc4161c, 0x0b7a4b),
            dark: ODTokens(0x1e1611, 0x352b24, 0x453d36, 0xf7f7f2, 0xf7f7f2, 0x352b24, 0xaeaaa5, 0x564e47, 0xf29169, 0xf05d23, 0x261c15, 0xc5d86d, 0xfd8789, 0x4cd08a)
        ),
        ODPalette(
            id: "bridlice", name: L("Břidlice a levandule"),
            light: ODTokens(0xd1d1df, 0xedeffb, 0xe0e1ee, 0x232c33, 0x232c33, 0xedeffb, 0x5c636b, 0xcfd2dd, 0x4d6a6a, 0x5a7d7c, 0xffffff, 0xa0c1d1, 0xc4161c, 0x0d7249),
            dark: ODTokens(0x1c2329, 0x303941, 0x3e4750, 0xdadff7, 0xdadff7, 0x303941, 0xaeb5c8, 0x4d5560, 0xa4b6c3, 0x5a7d7c, 0xffffff, 0xa0c1d1, 0xef9eab, 0x4cd08a)
        ),
        ODPalette(
            id: "karmin", name: L("Karmín a oranžová"),
            light: ODTokens(0xe7eaea, 0xfbfffe, 0xf2f5f5, 0x1b1b1e, 0x1b1b1e, 0xfbfffe, 0x6d6f70, 0xd9dddc, 0x96031a, 0x96031a, 0xffffff, 0xfaa916, 0xc4161c, 0x0b7a4b),
            dark: ODTokens(0x161618, 0x2b2b2e, 0x3d3d40, 0xfbfffe, 0xfbfffe, 0x2b2b2e, 0xa8aaab, 0x4e4f51, 0xd39aa3, 0x96031a, 0xffffff, 0xfaa916, 0xfe8386, 0x4cd08a)
        ),
        ODPalette(
            id: "ametyst", name: L("Ametyst a vanilka"),
            light: ODTokens(0xe0ddca, 0xf7f0d9, 0xede7d2, 0x330036, 0x330036, 0xf7f0d9, 0x7d5b74, 0xdaccc1, 0x38182f, 0x38182f, 0xffffff, 0x2f394d, 0xc4161c, 0x0d754a),
            dark: ODTokens(0x29002b, 0x40103f, 0x4f2249, 0xeee1b3, 0xeee1b3, 0x40103f, 0xb0978b, 0x5e3453, 0xeee1b3, 0xeee1b3, 0x330036, 0xeee1b3, 0xff6b6f, 0x4cd08a)
        ),
        ODPalette(
            id: "kastan", name: L("Kaštan"),
            light: ODTokens(0xdad4cd, 0xf5efed, 0xe9e3de, 0x24120f, 0x24120f, 0xf5efed, 0x6e615e, 0xd6cecc, 0x823329, 0x823329, 0xffffff, 0x8a3033, 0xc4161c, 0x0d7447),
            dark: ODTokens(0x1d0e0c, 0x32201d, 0x42312d, 0xeadeda, 0xeadeda, 0x32201d, 0xaa9d99, 0x51403d, 0xbc938c, 0x823329, 0xffffff, 0xbfb8ad, 0xfe7073, 0x4cd08a)
        ),
        ODPalette(
            id: "salvej", name: L("Šalvěj a mandarinka"),
            light: ODTokens(0xf3d4a3, 0xfffdde, 0xf9eac3, 0x1d1a05, 0x1d1a05, 0xfffdde, 0x6c6950, 0xdddbbd, 0xc03a23, 0xca3c25, 0xffffff, 0x7fb069, 0xc4161c, 0x0b7a4b),
            dark: ODTokens(0x171504, 0x2d2a12, 0x3f3c21, 0xfffbbd, 0xfffbbd, 0x2d2a12, 0xada97a, 0x514e2f, 0xe2946b, 0xca3c25, 0xffffff, 0x7fb069, 0xff7f7a, 0x4cd08a)
        )
    ]

    static func palette(id: String) -> ODPalette {
        all.first { $0.id == id } ?? all[0]
    }
}

extension Color {
    init(odHex hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

extension UIColor {
    convenience init(odHex hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

/// Hotové barvy pro aktuální paletu + světlý/tmavý režim.
struct ODColors {
    let bg: Color
    let surface: Color
    let surface2: Color
    let text: Color
    let muted: Color
    let line: Color
    let ink: Color
    let inkText: Color
    let accent: Color
    let sign: Color
    let signText: Color
    let mark: Color
    let bad: Color
    let good: Color
    let isDark: Bool

    init(tokens t: ODTokens, isDark: Bool) {
        bg = Color(odHex: t.bg)
        surface = Color(odHex: t.surface)
        surface2 = Color(odHex: t.surface2)
        text = Color(odHex: t.text)
        muted = Color(odHex: t.muted)
        line = Color(odHex: t.line)
        ink = Color(odHex: t.ink)
        inkText = Color(odHex: t.inkText)
        accent = Color(odHex: t.accent)
        sign = Color(odHex: t.sign)
        signText = Color(odHex: t.signText)
        mark = Color(odHex: t.mark)
        bad = Color(odHex: t.bad)
        good = Color(odHex: t.good)
        self.isDark = isDark
    }

    static func make(paletteId: String, isDark: Bool) -> ODColors {
        let p = ODPalette.palette(id: paletteId)
        return ODColors(tokens: isDark ? p.dark : p.light, isDark: isDark)
    }

    func lineColor(for cls: ODLineClass) -> Color {
        Color(odHex: ODTheme.lineHex(cls, dark: isDark))
    }
}

enum ODTheme {
    /// Barvy typů z webu: autobus, vlak, tramvaj, noční.
    static func lineHex(_ cls: ODLineClass, dark: Bool) -> UInt32 {
        switch cls {
        case .bus: return 0x1d5fd1
        case .train: return 0x0b7a4b
        case .tram: return 0xc4161c
        case .night: return dark ? 0x3a4350 : 0x1b1f27
        }
    }

    /// Klíč v `@AppStorage` pro zvolenou paletu.
    static let paletteKey = "od_palette"
}

private struct ODColorsKey: EnvironmentKey {
    static let defaultValue = ODColors.make(paletteId: "", isDark: false)
}

extension EnvironmentValues {
    var odColors: ODColors {
        get { self[ODColorsKey.self] }
        set { self[ODColorsKey.self] = newValue }
    }
}
