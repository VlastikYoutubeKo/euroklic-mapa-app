import CoreLocation
import SwiftUI
import WidgetKit

// MARK: - Model

struct ToiletPlace: Codable, Hashable, Identifiable {
    let id: Int
    let name: String
    /// Vzdušnou čarou v metrech od polohy v době stažení.
    let distanceM: Double
    /// Víc nahlášení "nefunguje" než ověření.
    let reported: Bool
}

struct ToiletSnapshot: Codable {
    let places: [ToiletPlace]
    let fetchedAt: Date
}

struct ToiletEntry: TimelineEntry {
    let date: Date
    let places: [ToiletPlace]
    /// Widget nemá oprávnění k poloze (nebo se nezaměřil) -> "Povolte polohu v appce".
    let needsLocation: Bool
    /// Síť selhala, ukazujeme poslední uložená data.
    let offline: Bool

    static let sample = ToiletEntry(
        date: Date(),
        places: [
            ToiletPlace(id: 1, name: L("Hlavní nádraží"), distanceM: 240, reported: false),
            ToiletPlace(id: 2, name: L("Nákupní centrum"), distanceM: 610, reported: false),
            ToiletPlace(id: 3, name: L("Městský park"), distanceM: 1180, reported: false),
        ],
        needsLocation: false,
        offline: false
    )
}

// MARK: - Načtení dat (GET /api_locations.php?near=&radius_km=20)

enum ToiletFetcher {
    private struct RawFeature: Decodable {
        struct Geo: Decodable { let coordinates: [Double]? }
        struct Props: Decodable {
            let id: Int?
            let name: String?
            let likes: Int?
            let dislikes: Int?

            enum CodingKeys: String, CodingKey { case id, name, likes, dislikes }

            init(from decoder: Decoder) throws {
                let c = try decoder.container(keyedBy: CodingKeys.self)
                if let n = try? c.decode(Int.self, forKey: .id) {
                    id = n
                } else if let s = try? c.decode(String.self, forKey: .id) {
                    id = Int(s)
                } else {
                    id = nil
                }
                name = try? c.decode(String.self, forKey: .name)
                likes = try? c.decode(Int.self, forKey: .likes)
                dislikes = try? c.decode(Int.self, forKey: .dislikes)
            }
        }

        let geometry: Geo?
        let properties: Props?

        enum CodingKeys: String, CodingKey { case geometry, properties }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            geometry = try? c.decode(Geo.self, forKey: .geometry)
            properties = try? c.decode(Props.self, forKey: .properties)
        }
    }

    /// Jedna vadná položka nesmí shodit celý seznam.
    private struct SafeFeature: Decodable {
        let value: RawFeature?
        init(from decoder: Decoder) throws { value = try? RawFeature(from: decoder) }
    }

    private struct Root: Decodable {
        let features: [SafeFeature]?
    }

    static func nearest(lat: Double, lon: Double, max: Int = 3) async throws -> [ToiletPlace] {
        var c = URLComponents(string: "https://euroklic.odjezdy.online/api_locations.php")!
        c.queryItems = [
            URLQueryItem(name: "near", value: "\(lat),\(lon)"),
            URLQueryItem(name: "radius_km", value: "20"),
        ]
        guard let url = c.url else { throw WidgetHTTPError.badBody }
        let data = try await WidgetHTTP.get(url)
        // Backend umí vrátit HTTP 200 s {"success":false} nebo HTML -> features chybí -> chyba.
        let root = try JSONDecoder().decode(Root.self, from: data)
        guard let features = root.features else { throw WidgetHTTPError.badBody }

        var places: [ToiletPlace] = []
        for item in features {
            guard let f = item.value, let p = f.properties, let id = p.id,
                  let coords = f.geometry?.coordinates, coords.count >= 2,
                  coords[0].isFinite, coords[1].isFinite else { continue }
            let likes = p.likes ?? 0
            let dislikes = p.dislikes ?? 0
            let rawName = (p.name ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            places.append(
                ToiletPlace(
                    id: id,
                    name: rawName.isEmpty ? L("Toaleta") : rawName,
                    distanceM: straightLineMeters(lat, lon, coords[1], coords[0]),
                    reported: dislikes > 0 && dislikes > likes
                )
            )
        }
        places.sort { $0.distanceM < $1.distanceM }
        return Array(places.prefix(max))
    }
}

// MARK: - Provider

private enum ToiletKeys {
    static let snapshot = "toilet.snapshot"
}

struct ToiletProvider: TimelineProvider {
    func placeholder(in context: Context) -> ToiletEntry { .sample }

    func getSnapshot(in context: Context, completion: @escaping (ToiletEntry) -> Void) {
        if context.isPreview {
            completion(.sample)
            return
        }
        if let cached = WidgetCache.load(ToiletSnapshot.self, key: ToiletKeys.snapshot), !cached.places.isEmpty {
            completion(ToiletEntry(date: Date(), places: cached.places, needsLocation: false, offline: false))
        } else {
            completion(.sample)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ToiletEntry>) -> Void) {
        Task { @MainActor in
            let entry = await Self.load()
            let next = Date().addingTimeInterval(widgetRefreshInterval)
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }

    @MainActor
    private static func load() async -> ToiletEntry {
        let now = Date()
        let cached = WidgetCache.load(ToiletSnapshot.self, key: ToiletKeys.snapshot)

        guard let location = await WidgetLocationProvider().currentLocation() else {
            // Bez polohy: poslední známé místo + výzva k povolení polohy.
            return ToiletEntry(date: now, places: cached?.places ?? [], needsLocation: true, offline: false)
        }

        do {
            let places = try await ToiletFetcher.nearest(
                lat: location.coordinate.latitude,
                lon: location.coordinate.longitude
            )
            if !places.isEmpty {
                WidgetCache.save(ToiletSnapshot(places: places, fetchedAt: now), key: ToiletKeys.snapshot)
            }
            return ToiletEntry(date: now, places: places, needsLocation: false, offline: false)
        } catch {
            return ToiletEntry(date: now, places: cached?.places ?? [], needsLocation: false, offline: true)
        }
    }
}

// MARK: - Widget

struct NearestToiletWidget: Widget {
    let kind = "NearestToiletWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ToiletProvider()) { entry in
            ToiletWidgetView(entry: entry)
        }
        .configurationDisplayName("Nejbližší WC")
        .description("Nejbližší toalety s Euroklíčem podle vaší polohy.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

// MARK: - Views

struct ToiletWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: ToiletEntry

    var body: some View {
        content
            .widgetURL(URL(string: "euroklicmapa://open"))
            .containerBackground(for: .widget) { widgetBackground(for: family) }
    }

    @ViewBuilder
    private var content: some View {
        switch family {
        case .accessoryInline:
            inlineView
        case .accessoryRectangular:
            rectangularView
        case .systemMedium:
            mediumView
        default:
            smallView
        }
    }

    private var statusLine: String? {
        if entry.needsLocation { return L("Povolte polohu v appce") }
        if entry.offline { return "Offline" }
        return nil
    }

    // Header (small / medium)
    private var header: some View {
        HStack(spacing: 4) {
            Image(systemName: "mappin.circle.fill")
                .foregroundStyle(WidgetColors.euroklicText)
                .accessibilityHidden(true)
            Text("Nejbližší WC")
                .font(.caption.weight(.semibold))
                .foregroundStyle(WidgetColors.euroklicText)
                .lineLimit(1)
            Spacer(minLength: 0)
            if entry.offline {
                Text("Offline")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func hint(_ text: String) -> some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(entry.needsLocation ? WidgetColors.warning : Color.secondary)
            .lineLimit(2)
            .minimumScaleFactor(0.8)
    }

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 4) {
            header
            if let first = entry.places.first {
                Spacer(minLength: 0)
                Text(first.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                Text(widgetDistance(first.distanceM))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(WidgetColors.euroklicText)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
                Text("pěšky \(widgetWalkingTime(first.distanceM))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                if first.reported {
                    Text("⚠ Nahlášeno jako nefunguje")
                        .font(.caption2)
                        .foregroundStyle(WidgetColors.warning)
                        .lineLimit(1)
                }
                if entry.needsLocation, let s = statusLine { hint(s) }
            } else {
                Spacer(minLength: 0)
                Text(entry.needsLocation ? L("Povolte polohu v appce") : L("V okolí 20 km nic nenalezeno"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(entry.needsLocation ? WidgetColors.warning : Color.primary)
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var mediumView: some View {
        VStack(alignment: .leading, spacing: 6) {
            header
            if entry.places.isEmpty {
                Spacer(minLength: 0)
                Text(entry.needsLocation ? L("Povolte polohu v appce") : L("V okolí 20 km nic nenalezeno"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(entry.needsLocation ? WidgetColors.warning : Color.primary)
                Spacer(minLength: 0)
            } else {
                ForEach(Array(entry.places.enumerated()), id: \.element.id) { index, place in
                    HStack(spacing: 8) {
                        Text(place.reported ? "⚠ \(place.name)" : place.name)
                            .font(index == 0 ? .subheadline.weight(.semibold) : .footnote)
                            .lineLimit(1)
                        Spacer(minLength: 4)
                        Text(widgetDistance(place.distanceM))
                            .font(index == 0 ? .subheadline.weight(.bold) : .footnote.weight(.semibold))
                            .foregroundStyle(WidgetColors.euroklicText)
                        Text(widgetWalkingTime(place.distanceM))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .frame(minWidth: 44, alignment: .trailing)
                    }
                    .accessibilityElement(children: .combine)
                }
                if entry.needsLocation, let s = statusLine { hint(s) }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var rectangularView: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let first = entry.places.first {
                Text("WC · \(first.name)")
                    .font(.headline)
                    .lineLimit(1)
                    .widgetAccentable()
                Text("\(widgetDistance(first.distanceM)) · pěšky \(widgetWalkingTime(first.distanceM))")
                    .font(.caption)
                    .lineLimit(1)
                if entry.needsLocation {
                    Text("Povolte polohu v appce").font(.caption2).lineLimit(1)
                } else if entry.offline {
                    Text("Offline").font(.caption2).lineLimit(1)
                }
            } else {
                Text("Nejbližší WC").font(.headline).widgetAccentable()
                Text(entry.needsLocation ? L("Povolte polohu v appce") : L("Nic v okolí"))
                    .font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var inlineView: some View {
        Group {
            if let first = entry.places.first {
                Text("WC \(widgetDistance(first.distanceM)) · \(first.name)")
            } else {
                Text(entry.needsLocation ? L("WC: povolte polohu") : L("WC: nic v okolí"))
            }
        }
    }
}
