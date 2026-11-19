import CoreLocation
import SwiftUI

/// A public-transport stop near a place; `distanceM` is straight-line metres.
struct NearbyStop: Identifiable, Hashable {
    let id: String
    let name: String
    let distanceM: Int
}

/// One upcoming departure from a stop (odjezdy.online `/api/board/{id}`).
struct Departure: Identifiable, Hashable {
    let id: String
    let line: String
    let destination: String
    let time: Date
    let delayMinutes: Int
}

/// Nearest stops and their next departures from the public app.odjezdy.online JSON
/// (no key; the site's terms allow reasonable personal use of the public endpoints).
/// Plain ephemeral session, so no cookies or auth tokens ever go to a third party.
/// Any failure -> [] (the UI hides the section).
enum NearbyStopsService {
    // MARK: Tolerant decoding (id may be a string or a number; one bad item must not drop the list)

    private struct Station: Decodable {
        let id: String?
        let name: String?
        let lat: Double?
        let lng: Double?

        enum CodingKeys: String, CodingKey { case id, name, lat, lng }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = Self.flexibleString(c, .id)
            name = try? c.decode(String.self, forKey: .name)
            lat = try? c.decode(Double.self, forKey: .lat)
            lng = try? c.decode(Double.self, forKey: .lng)
        }

        static func flexibleString(_ c: KeyedDecodingContainer<CodingKeys>, _ key: CodingKeys) -> String? {
            if let s = try? c.decode(String.self, forKey: key) { return s }
            if let n = try? c.decode(Int.self, forKey: key) { return String(n) }
            return nil
        }
    }

    private struct SafeStation: Decodable {
        let value: Station?
        init(from decoder: Decoder) throws { value = try? Station(from: decoder) }
    }

    private struct BoardItem: Decodable {
        let id: String?
        let line: String?
        let destination: String?
        let plannedTime: String?
        let delayMinutes: Int?

        enum CodingKeys: String, CodingKey { case id, line, destination, plannedTime, delayMinutes }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            if let s = try? c.decode(String.self, forKey: .id) { id = s } else if let n = try? c.decode(Int.self, forKey: .id) { id = String(n) } else { id = nil }
            line = try? c.decode(String.self, forKey: .line)
            destination = try? c.decode(String.self, forKey: .destination)
            plannedTime = try? c.decode(String.self, forKey: .plannedTime)
            delayMinutes = try? c.decode(Int.self, forKey: .delayMinutes)
        }
    }

    private struct SafeBoardItem: Decodable {
        let value: BoardItem?
        init(from decoder: Decoder) throws { value = try? BoardItem(from: decoder) }
    }

    private struct Board: Decodable {
        let data: [SafeBoardItem]?
    }

    // MARK: Requests

    private static func fetch(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue("EuroklicMapa-iOS/1.0 (euroklic.odjezdy.online)", forHTTPHeaderField: "User-Agent")
        return try await URLSession(configuration: .ephemeral).data(for: request).0
    }

    static func nearby(lat: Double, lon: Double, max: Int = 3) async -> [NearbyStop] {
        var c = URLComponents(string: "https://app.odjezdy.online/api/stations/bounds")!
        c.queryItems = [
            URLQueryItem(name: "minLat", value: String(lat - 0.008)),
            URLQueryItem(name: "maxLat", value: String(lat + 0.008)),
            URLQueryItem(name: "minLng", value: String(lon - 0.012)),
            URLQueryItem(name: "maxLng", value: String(lon + 0.012)),
        ]
        guard let url = c.url else { return [] }
        do {
            let data = try await fetch(url)
            let here = CLLocation(latitude: lat, longitude: lon)
            let stops: [NearbyStop] = try JSONDecoder().decode([SafeStation].self, from: data)
                .compactMap { $0.value }
                .compactMap { s in
                    guard let id = s.id, let name = s.name, !name.isEmpty,
                          let la = s.lat, let lo = s.lng, la.isFinite, lo.isFinite else { return nil }
                    let d = here.distance(from: CLLocation(latitude: la, longitude: lo))
                    return NearbyStop(id: id, name: name, distanceM: Int(d))
                }
            return nearestStops(stops, max: max)
        } catch {
            print("NearbyStops: \(error)")
            return []
        }
    }

    /// Next departures (soonest first) from one stop.
    static func departures(stopId: String, limit: Int = 4) async -> [Departure] {
        guard let url = URL(string: "https://app.odjezdy.online/api/board/\(stopId)") else { return [] }
        do {
            let data = try await fetch(url)
            let board = try JSONDecoder().decode(Board.self, from: data)
            let now = Date().addingTimeInterval(-60)
            let items: [Departure] = (board.data ?? []).compactMap { $0.value }.compactMap { item in
                guard let raw = item.plannedTime, let planned = parseISO(raw) else { return nil }
                let delay = item.delayMinutes ?? 0
                let actual = planned.addingTimeInterval(Double(delay) * 60)
                guard actual >= now else { return nil }
                return Departure(
                    id: item.id ?? "\(raw)-\(item.line ?? "")",
                    line: item.line ?? "",
                    destination: item.destination ?? "",
                    time: planned,
                    delayMinutes: delay,
                )
            }
            return Array(items.sorted { $0.time < $1.time }.prefix(limit))
        } catch {
            print("Departures: \(error)")
            return []
        }
    }

    private static func parseISO(_ s: String) -> Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: s) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: s)
    }

    /// Pure: sort by distance, dedupe by name (platform posts of one stop), keep `max`.
    static func nearestStops(_ stops: [NearbyStop], max: Int) -> [NearbyStop] {
        var seen = Set<String>()
        return stops.sorted { $0.distanceM < $1.distanceM }
            .filter { seen.insert($0.name).inserted }
            .prefix(max)
            .map { $0 }
    }
}
