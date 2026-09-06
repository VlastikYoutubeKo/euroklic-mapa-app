import CoreLocation
import Foundation

/// Offline-first store over both feeds: JSON cache in Documents, refreshed on
/// the background at startup and on demand. Mirrors Android `EuroklicRepository`:
/// geo-scoped fetch (`near` + 500 km) with full-feed fallback, empty response
/// never wipes the cache, `[lon, lat]` decoded and non-finite rows dropped.
@MainActor
final class PlacesStore: ObservableObject {
    @Published private(set) var wc: [PlaceItem] = []
    @Published private(set) var pickups: [PlaceItem] = []
    @Published private(set) var lastSync: Date?
    @Published var lastError: String?

    private let api = EuroklicAPI()
    private let cacheURL: URL = {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("places_cache.json")
    }()

    private struct CacheFile: Codable {
        var wc: [PlaceItem]
        var pickups: [PlaceItem]
        var syncedAt: Date?
    }

    init() {
        loadCache()
    }

    func loadCache() {
        guard let data = try? Data(contentsOf: cacheURL),
              let cached = try? JSONDecoder().decode(CacheFile.self, from: data)
        else { return }
        wc = cached.wc
        pickups = cached.pickups
        lastSync = cached.syncedAt
    }

    private func persist() {
        let file = CacheFile(wc: wc, pickups: pickups, syncedAt: lastSync)
        if let data = try? JSONEncoder().encode(file) {
            try? data.write(to: cacheURL, options: .atomic)
        }
    }

    /// Refresh both feeds concurrently. Scoped to the user's position when
    /// known (500 km — covers CZ/SK + surroundings), falls back to the full feed
    /// when the scoped answer is empty (user abroad). An empty response never
    /// wipes a good cache; failures keep the cache (offline-first) and only
    /// surface a gentle message.

    func refreshWC(near: String?) async -> [PlaceItem] {
        do {
            var raw = try await api.locations(near: near, radiusKm: near == nil ? nil : 500).features
            if raw.isEmpty, near != nil {
                raw = try await api.locations().features // user abroad → full feed
            }
            return raw.compactMap { feature in
                guard let g = feature.geometry, g.coordinates.count >= 2 else { return nil }
                let lon = g.coordinates[0], lat = g.coordinates[1]
                guard lon.isFinite, lat.isFinite else { return nil }
                let p = feature.properties
                return PlaceItem(
                    id: p.id,
                    isPickup: false,
                    name: (p.name?.isEmpty == false) ? p.name! : "Bezbariérové WC",
                    subtitle: nil,
                    latitude: lat,
                    longitude: lon,
                    source: p.source,
                    likes: p.likes ?? 0,
                    dislikes: p.dislikes ?? 0,
                    lastVerified: p.last_verified,
                    webUrl: p.web_url,
                    openingHours: p.opening_hours,
                    access: p.access,
                    wheelchair: p.wheelchair,
                    accessibilityNote: p.accessibility_note,
                    country: p.country,
                    floorPlanUrl: p.floor_plan_url,
                    photoUrl: p.photo_url,
                    precision: nil,
                    phone: nil,
                )
            }
        } catch {
            lastError = (error as? LocalizedError)?.errorDescription ?? "Refresh se nepodařil."
            return []
        }
    }

    func refreshPickups(near: String?) async -> [PlaceItem] {
        do {
            var features = try await api.pickupPoints(near: near, radiusKm: near == nil ? nil : 500).features
            if features.isEmpty, near != nil {
                features = try await api.pickupPoints().features
            }
            return features.compactMap { feature in
                guard let g = feature.geometry, g.coordinates.count >= 2 else { return nil }
                let lon = g.coordinates[0], lat = g.coordinates[1]
                guard lon.isFinite, lat.isFinite else { return nil }
                let p = feature.properties
                let subtitle = [p.district, p.kraj].compactMap { $0 }.joined(separator: ", ")
                return PlaceItem(
                    id: p.id,
                    isPickup: true,
                    name: (p.org_name?.isEmpty == false) ? p.org_name! : "Výdejní místo Euroklíče",
                    subtitle: subtitle.isEmpty ? p.address : subtitle,
                    latitude: lat,
                    longitude: lon,
                    source: nil,
                    likes: p.likes ?? 0,
                    dislikes: p.dislikes ?? 0,
                    lastVerified: nil,
                    webUrl: nil,
                    openingHours: nil,
                    access: nil,
                    wheelchair: nil,
                    accessibilityNote: nil,
                    country: nil,
                    floorPlanUrl: nil,
                    photoUrl: nil,
                    precision: p.precision,
                    phone: p.phone,
                )
            }
        } catch {
            lastError = (error as? LocalizedError)?.errorDescription ?? "Refresh se nepodařil."
            return []
        }
    }

    /// Combined refresh (WC + pickups concurrently). Freshness is only stamped
    /// when at least one feed actually delivered — a total failure keeps the old
    /// cache AND the old timestamp, so the freshness UI stays honest.
    func refreshAll(near: String?) async {
        async let w = refreshWC(near: near)
        async let p = refreshPickups(near: near)
        let (newWc, newPp) = await (w, p)
        if !newWc.isEmpty { wc = newWc }
        if !newPp.isEmpty { pickups = newPp }
        if !newWc.isEmpty || !newPp.isEmpty {
            lastSync = Date()
            persist()
        }
    }

    // MARK: - Derived

    enum Category: String, CaseIterable, Identifiable {
        case all, toilet, pickup
        var id: String { rawValue }
        var label: String {
            switch self {
            case .all: return "Vše"
            case .toilet: return "Toalety"
            case .pickup: return "Výdejní místa"
            }
        }
    }

    func flattened(category: Category, userLocation: CLLocation?) -> [PlaceItem] {
        var items: [PlaceItem] = []
        if category != .pickup { items += wc }
        if category != .toilet { items += pickups }
        if let userLocation {
            return items.sorted {
                distance(from: $0, to: userLocation) < distance(from: $1, to: userLocation)
            }
        }
        return items.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    func nearest(to location: CLLocation, category: Category = .all) -> PlaceItem? {
        flattened(category: category, userLocation: location).first
    }

    func distance(from item: PlaceItem, to location: CLLocation) -> CLLocationDistance {
        location.distance(from: CLLocation(latitude: item.latitude, longitude: item.longitude))
    }

    /// Mirror of the website's stats section; badge semantics: verified = 👍, reported = 👎 prevails.
    struct AppStats {
        var totalWc = 0
        var totalPickup = 0
        var verifiedWc = 0
        var reportedWc = 0
        var topLiked: PlaceItem?
        var topDisliked: PlaceItem?
    }

    var stats: AppStats {
        var s = AppStats(totalWc: wc.count, totalPickup: pickups.count)
        s.verifiedWc = wc.count { $0.likes > 0 }
        s.reportedWc = wc.count { $0.dislikes > 0 && $0.dislikes > $0.likes }
        s.topLiked = wc.filter { $0.likes > 0 }.max { $0.likes < $1.likes }
        s.topDisliked = wc.filter { $0.dislikes > 0 }.max { $0.dislikes < $1.dislikes }
        return s
    }
}
