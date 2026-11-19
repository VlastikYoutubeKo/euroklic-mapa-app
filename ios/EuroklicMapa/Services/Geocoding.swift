import CoreLocation
import Foundation

/// One address/place hit from Nominatim.
struct GeoResult: Identifiable, Hashable {
    let id: Int
    let name: String
    let coordinate: CLLocationCoordinate2D

    static func == (lhs: GeoResult, rhs: GeoResult) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

/// Address search via the public Nominatim API, limited to CZ/SK like the Android app.
/// Identifying User-Agent (Nominatim policy), ephemeral session, one request per submit.
enum Geocoder {
    private struct Hit: Decodable {
        let place_id: Int?
        let display_name: String?
        let lat: String?
        let lon: String?
    }

    static func search(_ query: String) async -> [GeoResult] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard q.count >= 2 else { return [] }
        var c = URLComponents(string: "https://nominatim.openstreetmap.org/search")!
        c.queryItems = [
            URLQueryItem(name: "q", value: q),
            URLQueryItem(name: "format", value: "jsonv2"),
            URLQueryItem(name: "limit", value: "5"),
            URLQueryItem(name: "countrycodes", value: "cz,sk"),
            URLQueryItem(name: "accept-language", value: "cs"),
        ]
        guard let url = c.url else { return [] }
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue("EuroklicMapa-iOS/1.0 (euroklic.odjezdy.online)", forHTTPHeaderField: "User-Agent")
        do {
            let (data, _) = try await URLSession(configuration: .ephemeral).data(for: request)
            let hits = try JSONDecoder().decode([Hit].self, from: data)
            return hits.enumerated().compactMap { index, hit in
                guard let name = hit.display_name, let lat = hit.lat.flatMap(Double.init), let lon = hit.lon.flatMap(Double.init),
                      lat.isFinite, lon.isFinite else { return nil }
                return GeoResult(id: hit.place_id ?? index, name: name, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon))
            }
        } catch {
            return []
        }
    }
}
