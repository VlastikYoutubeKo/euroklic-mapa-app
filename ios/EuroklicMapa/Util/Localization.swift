import Foundation

/// Translates a UI string that is not passed straight to a SwiftUI `Text`/`Label` (error messages,
/// computed titles, accessibility strings). The Czech source text is the key; `en.lproj` has the English.
func L(_ value: String.LocalizationValue) -> String { String(localized: value) }

/// True when the app is showing Czech (the development language). Used for plural rules and
/// number formatting that a string table can't express.
var appIsCzech: Bool { (Bundle.main.preferredLocalizations.first ?? "cs") == "cs" }
