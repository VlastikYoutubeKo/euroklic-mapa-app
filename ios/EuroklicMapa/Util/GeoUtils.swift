import CoreLocation
import Foundation

/// Port of Android `util/GeoUtils.kt` formatting (keep in sync).

/// "120 m" / "1,4 km" / "9273 km"
func formatDistance(_ meters: Double) -> String {
    let km = meters / 1000
    if meters < 1000 { return "\(Int(meters.rounded())) m" }
    if km < 100 { return String(format: "%.1f km", km).replacingOccurrences(of: ".", with: ",") }
    return "\(Int(km.rounded())) km"
}

/// Rough straight-line walking time at 4.5 km/h — "~7 min".
func formatWalkingTime(_ meters: Double) -> String {
    let minutes = Int((meters / 1000 / 4.5 * 60).rounded()).max(1)
    if minutes < 90 { return "~\(minutes) min" }
    if minutes < 24 * 60 { return "~\(Int((Double(minutes) / 60).rounded())) h" }
    return "přes 1 den"
}

/// Czech plural for "místo": 1 místo, 2–4 místa, 0 / 5+ míst.
func placesCount(_ n: Int) -> String {
    switch n {
    case 1: return "1 místo"
    case 2...4: return "\(n) místa"
    default: return "\(n) míst"
    }
}
