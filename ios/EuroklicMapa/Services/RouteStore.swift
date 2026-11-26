import CoreLocation
import MapKit
import SwiftUI

/// Cesta k vybranému místu: pěšky (čára na mapě po skutečných cestách) a MHD (jen odhad času,
/// Apple u veřejné dopravy nevrací geometrii; trasu s přestupy ukáže až aplikace Mapy po „Vyrazit“).
/// Pěšky se nabízí jen tam, kde to dává smysl (viz `maxWalkingSeconds`).
@MainActor
final class RouteStore: ObservableObject {
    enum Mode: String, CaseIterable, Identifiable {
        case walking, transit
        var id: String { rawValue }
        var symbol: String { self == .walking ? "figure.walk" : "tram.fill" }
        var title: String { self == .walking ? L("Pěšky") : L("MHD") }
    }

    struct Option: Equatable, Identifiable {
        let mode: Mode
        let seconds: TimeInterval
        let meters: CLLocationDistance?
        var id: Mode { mode }
    }

    /// Nejdelší pěší cesta, kterou ještě nabízíme. Delší (zhruba nad 3 km) je pro seniory, vozíčkáře
    /// a rodiče s kočárky nesmysl, takže se místo ní nabídne jen MHD.
    static let maxWalkingSeconds: TimeInterval = 45 * 60
    /// Dál než tohle se trasa ani nepočítá (jiné město, jiná země).
    static let maxStraightMeters: CLLocationDistance = 25_000

    @Published private(set) var options: [Option] = []
    @Published private(set) var selected: Mode?
    @Published private(set) var polyline: MKPolyline?
    @Published private(set) var isLoading = false
    /// Pěšky existuje, ale je to příliš daleko (ukáže se jen vysvětlení).
    @Published private(set) var walkingTooFar: Option?

    private var walkingLine: MKPolyline?
    private var currentKey: String?
    private var task: Task<Void, Never>?

    var selectedOption: Option? { options.first { $0.mode == selected } }

    /// Spočítá možnosti cesty. `key` identifikuje místo, ať pozdní `clear` starého detailu nesmaže nový výsledek.
    func load(key: String, from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) {
        if currentKey == key, (isLoading || !options.isEmpty || walkingTooFar != nil) { return }
        task?.cancel()
        reset()
        currentKey = key
        let straight = CLLocation(latitude: from.latitude, longitude: from.longitude)
            .distance(from: CLLocation(latitude: to.latitude, longitude: to.longitude))
        guard straight <= Self.maxStraightMeters else { return }
        isLoading = true
        task = Task { [weak self] in
            async let walking = Self.walkingRoute(from: from, to: to)
            async let transit = Self.transitEstimate(from: from, to: to)
            let (w, t) = await (walking, transit)
            guard let self, !Task.isCancelled, self.currentKey == key else { return }
            var list: [Option] = []
            if let w {
                let option = Option(mode: .walking, seconds: w.seconds, meters: w.meters)
                if w.seconds <= Self.maxWalkingSeconds {
                    list.append(option)
                    self.walkingLine = w.line
                } else {
                    self.walkingTooFar = option
                }
            }
            if let t { list.append(t) }
            self.options = list
            self.isLoading = false
            self.select(list.first?.mode)
        }
    }

    func select(_ mode: Mode?) {
        selected = mode
        polyline = (mode == .walking) ? walkingLine : nil
    }

    func clear(key: String) {
        guard currentKey == key else { return }
        task?.cancel()
        reset()
        currentKey = nil
    }

    private func reset() {
        options = []
        selected = nil
        polyline = nil
        walkingLine = nil
        walkingTooFar = nil
        isLoading = false
    }

    // MARK: - MapKit

    nonisolated private static func request(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D, type: MKDirectionsTransportType) -> MKDirections.Request {
        let request = MKDirections.Request()
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: from))
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: to))
        request.transportType = type
        request.requestsAlternateRoutes = false
        return request
    }

    nonisolated private static func walkingRoute(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D)
        async -> (seconds: TimeInterval, meters: CLLocationDistance, line: MKPolyline)? {
        guard let route = try? await MKDirections(request: request(from: from, to: to, type: .walking)).calculate().routes.first
        else { return nil }
        return (route.expectedTravelTime, route.distance, route.polyline)
    }

    nonisolated private static func transitEstimate(from: CLLocationCoordinate2D, to: CLLocationCoordinate2D) async -> Option? {
        guard let eta = try? await MKDirections(request: request(from: from, to: to, type: .transit)).calculateETA(),
              eta.expectedTravelTime > 0, eta.expectedTravelTime < 6 * 3600
        else { return nil }
        return Option(mode: .transit, seconds: eta.expectedTravelTime, meters: nil)
    }
}

extension TimeInterval {
    /// „~12 min“ / „~1 h 5 min“ (je to odhad, ne záruka).
    var routeDuration: String {
        let minutes = max(1, Int((self / 60).rounded()))
        if minutes < 60 { return "~\(minutes) min" }
        let h = minutes / 60, m = minutes % 60
        return m == 0 ? "~\(h) h" : "~\(h) h \(m) min"
    }
}
