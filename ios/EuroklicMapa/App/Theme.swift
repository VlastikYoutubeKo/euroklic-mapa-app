import SwiftUI

/// Brand tokens — must stay in sync with Android `ui/theme/Color.kt`
/// (contrast-verified there; do not tweak casually).
enum Theme {
    /// Filled-button blue — identical in light/dark on Android.
    static let brand = Color(hex: 0x2454E0)
    /// Brand blue for TEXT, icons and links: the same hue, lightened in Dark Mode
    /// so it keeps AA contrast on black (the filled-button blue above does not).
    static let tint = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0x8F / 255, green: 0xAE / 255, blue: 0xFF / 255, alpha: 1)
            : UIColor(red: 0x24 / 255, green: 0x54 / 255, blue: 0xE0 / 255, alpha: 1)
    })
    /// Amber for TEXT on a light background: `community` / the amber accent are fine as fills but fail
    /// AA contrast as small text on white, so warnings use this darker amber (lightened in Dark Mode).
    static let warningText = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0xFB / 255, green: 0xBF / 255, blue: 0x24 / 255, alpha: 1)
            : UIColor(red: 0x92 / 255, green: 0x54 / 255, blue: 0x00 / 255, alpha: 1)
    })
    /// Official source (ČD).
    static let official = Color(hex: 0x3B82F6)
    /// Community source (OSM, WCkompas, users).
    static let community = Color(hex: 0xF59E0B)
    /// Pickup points (slate square).
    static let pickup = Color(hex: 0x64748B)
    /// "Reported not working" accent.
    static let accent = Color(hex: 0xF59E0B)
    static let success = Color(hex: 0x16A34A)
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha,
        )
    }
}
