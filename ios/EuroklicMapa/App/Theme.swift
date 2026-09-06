import SwiftUI

/// Brand tokens — must stay in sync with Android `ui/theme/Color.kt`
/// (contrast-verified there; do not tweak casually).
enum Theme {
    /// Filled-button blue — identical in light/dark on Android.
    static let brand = Color(hex: 0x2454E0)
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
