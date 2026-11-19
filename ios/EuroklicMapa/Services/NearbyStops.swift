import CoreLocation
import SwiftUI

/// A public-transport stop near a place; `distanceM` is straight-line metres.
struct NearbyStop: Identifiable, Hashable {
    let id: String
    let name: String
    let distanceM: Int
}

/// Nearest stops from the public app.odjezdy.online JSON (no key; the site's terms
/// allow reasonable personal use of the public endpoints). Plain ephemeral session,
/// so no cookies or auth tokens ever go to a third party. Any failure -> [] (the card hides).
enum NearbyStopsService {
    /// Tolerant: id may be a string or a number, and one malformed station
    /// must not drop the whole list.
    private struct Station: Decodable {
        let id: String?
        let name: String?
        let lat: Double?
        let lng: Double?

        enum CodingKeys: String, CodingKey { case id, name, lat, lng }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            if let str = try? c.decode(String.self, forKey: .id) {
                id = str
            } else if let num = try? c.decode(Int.self, forKey: .id) {
                id = String(num)
            } else {
                id = nil
            }
            name = try? c.decode(String.self, forKey: .name)
            lat = try? c.decode(Double.self, forKey: .lat)
            lng = try? c.decode(Double.self, forKey: .lng)
        }
    }

    private struct Safe: Decodable {
        let station: Station?
        init(from decoder: Decoder) throws { station = try? Station(from: decoder) }
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
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue("EuroklicMapa-iOS/1.0 (euroklic.odjezdy.online)", forHTTPHeaderField: "User-Agent")
        do {
            let (data, _) = try await URLSession(configuration: .ephemeral).data(for: request)
            let here = CLLocation(latitude: lat, longitude: lon)
            let stops: [NearbyStop] = try JSONDecoder().decode([Safe].self, from: data).compactMap { $0.station }.compactMap { s in
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

    /// Pure: sort by distance, dedupe by name (platform posts of one stop), keep `max`.
    static func nearestStops(_ stops: [NearbyStop], max: Int) -> [NearbyStop] {
        var seen = Set<String>()
        return stops.sorted { $0.distanceM < $1.distanceM }
            .filter { seen.insert($0.name).inserted }
            .prefix(max)
            .map { $0 }
    }
}
