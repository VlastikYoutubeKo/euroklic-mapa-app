import Foundation

/// GeoJSON DTOs for the backend feeds.
/// Backend quirk: ANY `api_*.php` may answer HTTP 200 with `{"success":false}` or even the
/// homepage HTML (missing path) — decode failures must surface as a friendly error, never crash.

struct FeatureCollection<Properties: Decodable>: Decodable {
    let features: [Feature<Properties>]
}

struct Feature<Properties: Decodable>: Decodable {
    let geometry: Geometry?
    let properties: Properties
}

struct Geometry: Decodable {
    /// WGS84 — the backend sends **[lon, lat]** in this order.
    let coordinates: [Double]?
}

// MARK: - WC locations (`api_locations.php`)

struct WcProperties: Decodable {
    let id: Int
    let name: String?
    let source: String?
    let description: String?
    let note: String?
    /// May be `""` for a few old rows, not just `null`.
    let photo_url: String?
    let likes: Int?
    let dislikes: Int?
    /// `"YYYY-MM-DD HH:MM:SS"` UTC, auto-set on upvotes; mostly null.
    let last_verified: String?
    /// `/lokace/{id}-{slug}` — real route on the web.
    let web_url: String?
    /// Free text, ČD stations only.
    let opening_hours: String?
    /// `"eurokey"` for cd+osm, `"unknown"` elsewhere.
    let access: String?
    /// `"yes" | "no" | "unknown"` — real ČD building accessibility.
    let wheelchair: String?
    /// Raw multi-line ČD note.
    let accessibility_note: String?
    /// ISO-2 point-in-polygon, or null.
    let country: String?
    /// Direct ČD `/planek/{planekId}` page; ~60/109 ČD rows.
    let floor_plan_url: String?
}

// MARK: - Pickup points (`api_pickup_points.php`)

struct PickupProperties: Decodable {
    let id: Int
    let org_name: String?
    let address: String?
    let district: String?
    let kraj: String?
    /// May hold several comma-separated numbers — use the first for `tel:`.
    let phone: String?
    /// `"address" | "approx"` (~29 approx).
    let precision: String?
    let likes: Int?
    let dislikes: Int?
}
