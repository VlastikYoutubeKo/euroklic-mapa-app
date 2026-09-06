import CoreLocation
import Foundation

/// Unified row over both feeds (WC + pickup), the Swift mirror of Android's
/// `PlaceListItem`. All trust fields are recomputed per render — never cached.
struct PlaceItem: Identifiable, Hashable {
    let id: Int
    let isPickup: Bool
    let name: String
    let subtitle: String?
    let latitude: Double
    let longitude: Double
    let source: String?
    let likes: Int
    let dislikes: Int
    let lastVerified: String?
    let webUrl: String?
    let openingHours: String?
    let access: String?
    let wheelchair: String?
    let accessibilityNote: String?
    let country: String?
    let floorPlanUrl: String?
    let photoUrl: String?
    /// Pickup only: `"address" | "approx"`.
    let precision: String?
    /// Pickup only: first phone out of a possibly comma-separated list.
    let phone: String?

    var favoriteKey: String { "\(isPickup ? "pp" : "wc")_\(id)" }
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var isOfficialSource: Bool { source?.lowercased() == "cd" }

    var sourceLabel: String {
        if isPickup { return "Výdejní místo klíče" }
        return isOfficialSource ? "Oficiální zdroj" : "Komunitní zdroj"
    }

    var countryLabel: String? {
        guard let country, !country.isEmpty else { return nil }
        switch country.uppercased() {
        case "CZ": return "Česko"
        case "SK": return "Slovensko"
        default: return "Zahraničí (\(country.uppercased()))"
        }
    }

    var phonePrimary: String? {
        guard let phone, !phone.isEmpty else { return nil }
        return phone.split(separator: ",").first.map { $0.trimmingCharacters(in: .whitespaces) }
    }
}

// MARK: - Trust state (ported 1:1 from Android `util/PlaceStatus.kt`)

/// Mirrors the website's marker/status logic exactly — recompute per render
/// from live likes/dislikes/last_verified, never cache.
enum PlaceStatus {
    /// dislikes dominate — hollow marker, "⚠ Nahlášeno nefunguje".
    case reported
    /// source == "cd" — filled + ring, "✓ Oficiální zdroj (ČD)".
    case official
    /// last_verified within 183 days — filled + ring, "✓ Nedávno ověřeno".
    case recentlyVerified
    /// nothing above — filled, no ring.
    case unverified
}

private let verifiedWindowDays = 183

private let lastVerifiedFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = TimeZone(identifier: "UTC")
    f.dateFormat = "yyyy-MM-dd HH:mm:ss"
    return f
}()

func placeStatus(source: String?, likes: Int, dislikes: Int, lastVerified: String?, now: Date = Date()) -> PlaceStatus {
    if dislikes > 0 && dislikes > likes { return .reported }
    if source?.lowercased() == "cd" { return .official }
    if let lastVerified, !lastVerified.isEmpty,
       let verifiedAt = lastVerifiedFormatter.date(from: lastVerified),
       now.timeIntervalSince(verifiedAt) < Double(verifiedWindowDays) * 86_400 {
        return .recentlyVerified
    }
    return .unverified
}

extension PlaceItem {
    var status: PlaceStatus {
        placeStatus(source: source, likes: likes, dislikes: dislikes, lastVerified: lastVerified)
    }
}

/// Community-trust text badge — mirrors the website popup badge 1:1: purely
/// likes/dislikes/`cd`, deliberately NOT the 183-day window (that drives the
/// marker ring, not this text).
enum VoteBadge {
    case reported
    case verified
    case cdNoVotes
    case none
}

func voteBadge(likes: Int, dislikes: Int, isCd: Bool) -> VoteBadge {
    if dislikes > 0 && dislikes > likes { return .reported }
    if likes > 0 { return .verified }
    if isCd { return .cdNoVotes }
    return .none
}

extension VoteBadge {
    func label(likes: Int, dislikes: Int) -> String {
        switch self {
        case .reported: return "Nahlášeno nefunguje (\(dislikes)👎)"
        case .verified: return "Ověřeno · \(likes)👍"
        case .cdNoVotes: return "Zatím bez hlasů"
        case .none: return ""
        }
    }
}

extension PlaceItem {
    var badgeLabel: String? {
        let badge = voteBadge(likes: likes, dislikes: dislikes, isCd: isOfficialSource)
        return badge == .none ? nil : badge.label(likes: likes, dislikes: dislikes)
    }
}
