import CoreLocation
import SwiftUI
import WidgetKit

// MARK: - Model

struct DepartureItem: Codable, Hashable, Identifiable {
    let id: String
    let line: String
    let destination: String
    /// Skutečný (očekávaný) čas odjezdu.
    let time: Date
    /// Zpoždění v minutách (0 = včas / neznámé).
    let delayMinutes: Int
}

struct DeparturesSnapshot: Codable {
    let stopId: String
    let stopName: String
    let stopLat: Double
    let stopLon: Double
    /// Poloha, ze které se zastávka hledala (kvůli znovupoužití bez dalšího volání /stations/bounds).
    let originLat: Double
    let originLon: Double
    let distanceM: Double
    let departures: [DepartureItem]
    let fetchedAt: Date
}

struct DeparturesEntry: TimelineEntry {
    let date: Date
    let snapshot: DeparturesSnapshot?
    let needsLocation: Bool
    let offline: Bool

    /// Nejbližší nadcházející odjezdy vzhledem k času záznamu (tolerance 30 s).
    var upcoming: [DepartureItem] {
        guard let s = snapshot else { return [] }
        let cutoff = date.addingTimeInterval(-30)
        return Array(s.departures.filter { $0.time >= cutoff }.sorted { $0.time < $1.time }.prefix(3))
    }

    static let sample = DeparturesEntry(
        date: Date(),
        snapshot: DeparturesSnapshot(
            stopId: "CIS-1",
            stopName: L("Hlavní nádraží"),
            stopLat: 50.0, stopLon: 14.0, originLat: 50.0, originLon: 14.0,
            distanceM: 180,
            departures: [
                DepartureItem(id: "a", line: "9", destination: L("Sídliště"), time: Date().addingTimeInterval(3 * 60), delayMinutes: 0),
                DepartureItem(id: "b", line: "22", destination: L("Náměstí"), time: Date().addingTimeInterval(8 * 60), delayMinutes: 2),
                DepartureItem(id: "c", line: "136", destination: L("Nemocnice"), time: Date().addingTimeInterval(14 * 60), delayMinutes: 0),
            ],
            fetchedAt: Date()
        ),
        needsLocation: false,
        offline: false
    )
}

// MARK: - Načtení dat (app.odjezdy.online)

enum DeparturesFetcher {
    // Tolerantní dekódování: id může být string i číslo, jedna vadná položka nesmí shodit seznam.

    private struct Station: Decodable {
        let id: String?
        let name: String?
        let lat: Double?
        let lng: Double?

        enum CodingKeys: String, CodingKey { case id, name, lat, lng }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            if let s = try? c.decode(String.self, forKey: .id) {
                id = s
            } else if let n = try? c.decode(Int.self, forKey: .id) {
                id = String(n)
            } else {
                id = nil
            }
            name = try? c.decode(String.self, forKey: .name)
            lat = try? c.decode(Double.self, forKey: .lat)
            lng = try? c.decode(Double.self, forKey: .lng)
        }
    }

    private struct SafeStation: Decodable {
        let value: Station?
        init(from decoder: Decoder) throws { value = try? Station(from: decoder) }
    }

    private struct Item: Decodable {
        let id: String?
        let line: String?
        let destination: String?
        let plannedTime: String?
        let expectedTime: String?
        let delayMinutes: Int?
        let cancelled: Bool?

        enum CodingKeys: String, CodingKey {
            case id, line, destination, plannedTime, expectedTime, delayMinutes, cancelled
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            if let s = try? c.decode(String.self, forKey: .id) {
                id = s
            } else if let n = try? c.decode(Int.self, forKey: .id) {
                id = String(n)
            } else {
                id = nil
            }
            line = try? c.decode(String.self, forKey: .line)
            destination = try? c.decode(String.self, forKey: .destination)
            plannedTime = try? c.decode(String.self, forKey: .plannedTime)
            expectedTime = try? c.decode(String.self, forKey: .expectedTime)
            if let n = try? c.decode(Int.self, forKey: .delayMinutes) {
                delayMinutes = n
            } else if let d = try? c.decode(Double.self, forKey: .delayMinutes) {
                delayMinutes = Int(d.rounded())
            } else {
                delayMinutes = nil
            }
            cancelled = try? c.decode(Bool.self, forKey: .cancelled)
        }
    }

    private struct SafeItem: Decodable {
        let value: Item?
        init(from decoder: Decoder) throws { value = try? Item(from: decoder) }
    }

    private struct Board: Decodable {
        let data: [SafeItem]?
    }

    private static let base = "https://app.odjezdy.online"

    /// Nejbližší zastávka v bboxu ±0.008 lat / ±0.012 lng; vzdálenost se dopočítá.
    static func nearestStop(lat: Double, lon: Double) async throws -> (id: String, name: String, lat: Double, lon: Double, distanceM: Double)? {
        var c = URLComponents(string: "\(base)/api/stations/bounds")!
        c.queryItems = [
            URLQueryItem(name: "minLat", value: String(lat - 0.008)),
            URLQueryItem(name: "maxLat", value: String(lat + 0.008)),
            URLQueryItem(name: "minLng", value: String(lon - 0.012)),
            URLQueryItem(name: "maxLng", value: String(lon + 0.012)),
        ]
        guard let url = c.url else { throw WidgetHTTPError.badBody }
        let data = try await WidgetHTTP.get(url)
        let stations = try JSONDecoder().decode([SafeStation].self, from: data)

        var best: (id: String, name: String, lat: Double, lon: Double, distanceM: Double)?
        for item in stations {
            guard let s = item.value, let id = s.id, let name = s.name, !name.isEmpty,
                  let la = s.lat, let lo = s.lng, la.isFinite, lo.isFinite else { continue }
            let d = straightLineMeters(lat, lon, la, lo)
            if best == nil || d < best!.distanceM {
                best = (id: id, name: name, lat: la, lon: lo, distanceM: d)
            }
        }
        return best
    }

    /// Nadcházející odjezdy (bez minulých a zrušených), nejbližší první.
    static func departures(stopId: String, limit: Int = 6) async throws -> [DepartureItem] {
        var c = URLComponents(string: "\(base)/api/v2/departures")!
        c.queryItems = [
            URLQueryItem(name: "stopId", value: stopId),
            URLQueryItem(name: "limit", value: String(limit)),
        ]
        guard let url = c.url else { throw WidgetHTTPError.badBody }
        let data = try await WidgetHTTP.get(url)
        // Chybové tělo v2 je {"apiVersion":2,"error":{...}} -> data chybí -> chyba.
        let board = try JSONDecoder().decode(Board.self, from: data)
        guard let items = board.data else { throw WidgetHTTPError.badBody }

        let cutoff = Date().addingTimeInterval(-60)
        var result: [DepartureItem] = []
        for entry in items {
            guard let item = entry.value, item.cancelled != true,
                  let rawPlanned = item.plannedTime, let planned = parseTime(rawPlanned) else { continue }
            let delay = item.delayMinutes ?? 0
            let actual = item.expectedTime.flatMap { parseTime($0) } ?? planned.addingTimeInterval(Double(delay) * 60)
            guard actual >= cutoff else { continue }
            result.append(
                DepartureItem(
                    id: item.id ?? "\(rawPlanned)-\(item.line ?? "")",
                    line: item.line ?? "",
                    destination: item.destination ?? "",
                    time: actual,
                    delayMinutes: delay
                )
            )
        }
        result.sort { $0.time < $1.time }
        return result
    }

    /// "2026-11-19T06:20:00.000Z", "…Z", "…+01:00" i varianta s mezerou místo "T".
    static func parseTime(_ raw: String) -> Date? {
        var s = raw.trimmingCharacters(in: .whitespaces)
        if s.count > 10 {
            let idx = s.index(s.startIndex, offsetBy: 10)
            if s[idx] == " " { s.replaceSubrange(idx...idx, with: "T") }
        }
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }
}

// MARK: - Provider

private enum DepartureKeys {
    static let snapshot = "departures.snapshot"
}

struct DeparturesProvider: TimelineProvider {
    func placeholder(in context: Context) -> DeparturesEntry { .sample }

    func getSnapshot(in context: Context, completion: @escaping (DeparturesEntry) -> Void) {
        if context.isPreview {
            completion(.sample)
            return
        }
        if let cached = WidgetCache.load(DeparturesSnapshot.self, key: DepartureKeys.snapshot) {
            completion(DeparturesEntry(date: Date(), snapshot: cached, needsLocation: false, offline: false))
        } else {
            completion(.sample)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DeparturesEntry>) -> Void) {
        Task { @MainActor in
            let now = Date()
            let base = await Self.load(now: now)
            // Několik záznamů, aby se "za N min" a vypadávání odjezdů aktualizovaly i bez nového stahování.
            let entries = [0.0, 5.0, 10.0].map { minutes in
                DeparturesEntry(
                    date: now.addingTimeInterval(minutes * 60),
                    snapshot: base.snapshot,
                    needsLocation: base.needsLocation,
                    offline: base.offline
                )
            }
            completion(Timeline(entries: entries, policy: .after(now.addingTimeInterval(widgetRefreshInterval))))
        }
    }

    @MainActor
    private static func load(now: Date) async -> DeparturesEntry {
        let cached = WidgetCache.load(DeparturesSnapshot.self, key: DepartureKeys.snapshot)

        guard let location = await WidgetLocationProvider().currentLocation() else {
            return DeparturesEntry(date: now, snapshot: cached, needsLocation: true, offline: false)
        }
        let lat = location.coordinate.latitude
        let lon = location.coordinate.longitude

        do {
            // Zastávku znovu nehledáme, pokud jsme se od minula skoro nepohnuli.
            let stopId: String
            let stopName: String
            let stopLat: Double
            let stopLon: Double
            var originLat = lat
            var originLon = lon
            if let c = cached, straightLineMeters(lat, lon, c.originLat, c.originLon) < 150 {
                stopId = c.stopId
                stopName = c.stopName
                stopLat = c.stopLat
                stopLon = c.stopLon
                originLat = c.originLat
                originLon = c.originLon
            } else if let found = try await DeparturesFetcher.nearestStop(lat: lat, lon: lon) {
                stopId = found.id
                stopName = found.name
                stopLat = found.lat
                stopLon = found.lon
            } else {
                // V okolí není žádná zastávka.
                return DeparturesEntry(date: now, snapshot: nil, needsLocation: false, offline: false)
            }

            let deps = try await DeparturesFetcher.departures(stopId: stopId)
            let snapshot = DeparturesSnapshot(
                stopId: stopId,
                stopName: stopName,
                stopLat: stopLat,
                stopLon: stopLon,
                originLat: originLat,
                originLon: originLon,
                distanceM: straightLineMeters(lat, lon, stopLat, stopLon),
                departures: deps,
                fetchedAt: now
            )
            WidgetCache.save(snapshot, key: DepartureKeys.snapshot)
            return DeparturesEntry(date: now, snapshot: snapshot, needsLocation: false, offline: false)
        } catch {
            return DeparturesEntry(date: now, snapshot: cached, needsLocation: false, offline: true)
        }
    }
}

// MARK: - Widget

struct DeparturesWidget: Widget {
    let kind = "DeparturesWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DeparturesProvider()) { entry in
            DeparturesWidgetView(entry: entry)
        }
        .configurationDisplayName("Odjezdy poblíž")
        .description("Nejbližší zastávka a její nejbližší odjezdy.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

// MARK: - Views

struct DeparturesWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: DeparturesEntry

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
            listView(compact: false)
        default:
            listView(compact: true)
        }
    }

    /// "za 4 min" / "teď"
    private func relative(_ d: DepartureItem) -> String {
        let minutes = Int((d.time.timeIntervalSince(entry.date) / 60).rounded(.up))
        if minutes <= 0 { return L("teď") }
        if minutes < 60 { return "za \(minutes) min" }
        return pragueClock(d.time)
    }

    private var header: some View {
        HStack(spacing: 4) {
            Image(systemName: "bus.fill")
                .foregroundStyle(WidgetColors.odjezdyText)
                .accessibilityHidden(true)
            Text(entry.snapshot?.stopName ?? L("Odjezdy poblíž"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(WidgetColors.odjezdyText)
                .lineLimit(1)
            Spacer(minLength: 0)
            if entry.offline {
                Text("Offline")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func lineBadge(_ line: String) -> some View {
        Text(line.isEmpty ? "?" : line)
            .font(.caption.weight(.bold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .padding(.horizontal, 5)
            .frame(minWidth: 30, minHeight: 20)
            .background(WidgetColors.odjezdy, in: RoundedRectangle(cornerRadius: 5, style: .continuous))
    }

    @ViewBuilder
    private func row(_ d: DepartureItem, compact: Bool) -> some View {
        HStack(spacing: 6) {
            lineBadge(d.line)
            Text(d.destination)
                .font(compact ? .caption : .footnote)
                .lineLimit(1)
            Spacer(minLength: 2)
            if d.delayMinutes > 0 {
                Text("+\(d.delayMinutes)")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(WidgetColors.warning)
            }
            Text(compact ? relative(d) : "\(relative(d)) · \(pragueClock(d.time))")
                .font((compact ? Font.caption : Font.footnote).weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .accessibilityElement(children: .combine)
    }

    private func listView(compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: compact ? 5 : 6) {
            header
            if entry.snapshot == nil {
                Spacer(minLength: 0)
                Text(entry.needsLocation ? L("Povolte polohu v appce") : L("V okolí není žádná zastávka"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(entry.needsLocation ? WidgetColors.warning : Color.primary)
                Spacer(minLength: 0)
            } else {
                if !compact, let s = entry.snapshot {
                    Text("\(widgetDistance(s.distanceM)) · pěšky \(widgetWalkingTime(s.distanceM))")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                let deps = entry.upcoming
                if deps.isEmpty {
                    Text("Žádné další odjezdy")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(deps) { d in
                        row(d, compact: compact)
                    }
                }
                if entry.needsLocation {
                    Text("Povolte polohu v appce")
                        .font(.caption2)
                        .foregroundStyle(WidgetColors.warning)
                }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var rectangularView: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let s = entry.snapshot {
                Text(s.stopName)
                    .font(.headline)
                    .lineLimit(1)
                    .widgetAccentable()
                let deps = Array(entry.upcoming.prefix(2))
                if deps.isEmpty {
                    Text(entry.needsLocation ? L("Povolte polohu v appce") : L("Žádné další odjezdy"))
                        .font(.caption)
                } else {
                    ForEach(deps) { d in
                        Text("\(d.line) \(d.destination) · \(pragueClock(d.time))")
                            .font(.caption)
                            .lineLimit(1)
                    }
                }
            } else {
                Text("Odjezdy poblíž").font(.headline).widgetAccentable()
                Text(entry.needsLocation ? L("Povolte polohu v appce") : L("Žádná zastávka"))
                    .font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var inlineView: some View {
        Group {
            if let d = entry.upcoming.first {
                Text("\(d.line) → \(d.destination) \(pragueClock(d.time))")
            } else {
                Text(entry.needsLocation ? L("Odjezdy: povolte polohu") : L("Odjezdy: nic"))
            }
        }
    }
}
