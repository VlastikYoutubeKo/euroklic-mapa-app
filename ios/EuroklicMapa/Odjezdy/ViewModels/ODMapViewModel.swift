import CoreLocation
import MapKit
import SwiftUI

/// Kulaté filtry nahoře na mapě (jako v aplikaci DÚK).
enum ODMapFilter: String, CaseIterable, Identifiable {
    case bus, train, tram, ferry, stops

    var id: String { rawValue }

    var title: String {
        switch self {
        case .bus: return L("Autobus")
        case .train: return L("Vlak")
        case .tram: return L("Tramvaj a trolejbus")
        case .ferry: return L("Přívoz")
        case .stops: return L("Zastávky")
        }
    }

    var symbol: String {
        switch self {
        case .bus: return "bus.fill"
        case .train: return "train.side.front.car"
        case .tram: return "tram.fill"
        case .ferry: return "ferry.fill"
        case .stops: return "mappin.circle.fill"
        }
    }

    var isVehicleFilter: Bool { self != .stops }

    static func filter(for type: ODTransportType) -> ODMapFilter {
        switch type {
        case .bus, .other: return .bus
        case .train: return .train
        case .tram, .trolleybus, .subway: return .tram
        case .ferry: return .ferry
        }
    }
}

enum ODNotice: Equatable {
    case offline
    case rateLimited
    case error(String)
}

struct ODRecenter: Equatable {
    let id = UUID()
    let center: CLLocationCoordinate2D
    let meters: Double

    static func == (a: ODRecenter, b: ODRecenter) -> Bool { a.id == b.id }
}

@MainActor
final class ODMapViewModel: ObservableObject {
    /// Nejvíc vozidel zároveň na mapě (zbytek se zahodí podle vzdálenosti od středu).
    static let maxMarkers = 350
    /// Od tohoto rozpětí (stupně šířky) se vozidla nezobrazují (celá republika).
    static let maxLatSpan = 1.5
    /// Zastávky se načítají až od tohoto přiblížení.
    static let stopsLatSpan = 0.06

    @Published private(set) var vehicles: [ODVehicle] = []
    @Published private(set) var displayed: [ODVehicle] = []
    @Published private(set) var truncated = false
    @Published private(set) var tooFar = false
    @Published private(set) var stops: [ODStop] = []
    @Published private(set) var lastFetch: Date?
    @Published private(set) var notice: ODNotice?
    @Published var enabled: Set<ODMapFilter> = Set(ODMapFilter.allCases) {
        didSet {
            recompute()
            updatePolling()
            scheduleStops()
        }
    }

    @Published private(set) var selectedVehicleId: String?
    @Published private(set) var route: ODVehicleRoute?
    @Published private(set) var info: ODVehicleInfo?
    @Published private(set) var avgDelay: ODDelayAverage?
    @Published var openStop: ODStop?
    @Published var showVehicleDetail = false
    @Published private(set) var recenter: ODRecenter?

    private var selectedSnapshot: ODVehicle?
    private var region: MKCoordinateRegion?
    private var pollTask: Task<Void, Never>?
    private var stopsTask: Task<Void, Never>?
    private var selectionTask: Task<Void, Never>?
    private var routeLoadedAt: Date?
    private var tabVisible = false
    private var sceneActive = true

    private let api = ODAPIClient.shared

    nonisolated init() {}

    // MARK: Viditelnost a polling

    func setTabVisible(_ visible: Bool) {
        tabVisible = visible
        updatePolling()
    }

    func setSceneActive(_ active: Bool) {
        sceneActive = active
        updatePolling()
    }

    private var shouldPoll: Bool {
        tabVisible && sceneActive && enabled.contains { $0.isVehicleFilter }
    }

    private func updatePolling() {
        if shouldPoll {
            startPolling()
        } else {
            pollTask?.cancel()
            pollTask = nil
        }
    }

    private func startPolling() {
        guard pollTask == nil else { return }
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let wait = await self.pollOnce()
                if Task.isCancelled { return }
                try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            }
        }
    }

    /// Jedno volání `/api/vehicles?fields=map`; vrací, kolik sekund čekat do dalšího.
    private func pollOnce() async -> TimeInterval {
        do {
            let list = try await api.vehicles()
            vehicles = list
            lastFetch = Date()
            notice = nil
            if let id = selectedVehicleId, let fresh = list.first(where: { $0.id == id }) {
                selectedSnapshot = fresh
            }
            recompute()
            await refreshRouteIfStale()
            return 16
        } catch is CancellationError {
            return 16
        } catch let error as ODAPIError {
            switch error {
            case .rateLimited(let until):
                notice = .rateLimited
                return max(5, until.timeIntervalSinceNow + 1)
            case .offline:
                notice = .offline
                return 30
            default:
                notice = .error(error.errorDescription ?? L("Chyba"))
                return 30
            }
        } catch {
            notice = .error(L("Nepodařilo se načíst vozidla."))
            return 30
        }
    }

    // MARK: Viewport

    func regionChanged(_ newRegion: MKCoordinateRegion) {
        region = newRegion
        recompute()
        scheduleStops()
    }

    private func recompute() {
        guard let r = region else {
            displayed = []
            return
        }
        let latSpan = r.span.latitudeDelta
        if latSpan > Self.maxLatSpan {
            tooFar = true
            truncated = false
            displayed = selectedOnly()
            return
        }
        tooFar = false
        let minLat = r.center.latitude - latSpan * 0.6
        let maxLat = r.center.latitude + latSpan * 0.6
        let lngSpan = r.span.longitudeDelta
        let minLng = r.center.longitude - lngSpan * 0.6
        let maxLng = r.center.longitude + lngSpan * 0.6

        var list: [ODVehicle] = []
        list.reserveCapacity(512)
        for v in vehicles {
            guard v.isInService else { continue }
            guard enabled.contains(ODMapFilter.filter(for: v.type)) else { continue }
            guard v.lat >= minLat, v.lat <= maxLat, v.lng >= minLng, v.lng <= maxLng else { continue }
            list.append(v)
        }
        if list.count > Self.maxMarkers {
            let c = r.center
            list.sort { a, b in
                let da = (a.lat - c.latitude) * (a.lat - c.latitude) + (a.lng - c.longitude) * (a.lng - c.longitude)
                let db = (b.lat - c.latitude) * (b.lat - c.latitude) + (b.lng - c.longitude) * (b.lng - c.longitude)
                return da < db
            }
            list = Array(list.prefix(Self.maxMarkers))
            truncated = true
        } else {
            truncated = false
        }
        // Vybrané vozidlo musí zůstat na mapě.
        if let sel = selectedVehicle, !list.contains(where: { $0.id == sel.id }) {
            list.append(sel)
        }
        displayed = list
    }

    private func selectedOnly() -> [ODVehicle] {
        if let sel = selectedVehicle { return [sel] }
        return []
    }

    private func scheduleStops() {
        stopsTask?.cancel()
        guard enabled.contains(.stops), let r = region, r.span.latitudeDelta <= Self.stopsLatSpan else {
            if !stops.isEmpty { stops = [] }
            return
        }
        stopsTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 450_000_000)
            guard !Task.isCancelled, let self else { return }
            let padLat = r.span.latitudeDelta * 0.6
            let padLng = r.span.longitudeDelta * 0.6
            do {
                let list = try await self.api.stops(
                    minLat: r.center.latitude - padLat, maxLat: r.center.latitude + padLat,
                    minLng: r.center.longitude - padLng, maxLng: r.center.longitude + padLng
                )
                if Task.isCancelled { return }
                self.stops = Array(list.prefix(250))
            } catch {
                // Zastávky jsou doplněk; chybu nehlásíme.
            }
        }
    }

    // MARK: Výběr vozidla

    var selectedVehicle: ODVehicle? {
        guard let id = selectedVehicleId else { return nil }
        return vehicles.first { $0.id == id } ?? selectedSnapshot
    }

    func selectVehicle(_ vehicle: ODVehicle) {
        guard selectedVehicleId != vehicle.id else { return }
        selectedVehicleId = vehicle.id
        selectedSnapshot = vehicle
        route = nil
        info = nil
        avgDelay = nil
        routeLoadedAt = nil
        showVehicleDetail = false
        selectionTask?.cancel()
        let id = vehicle.id
        selectionTask = Task { [weak self] in
            await self?.loadDetails(id: id)
        }
    }

    func clearSelection() {
        selectionTask?.cancel()
        selectedVehicleId = nil
        selectedSnapshot = nil
        route = nil
        info = nil
        avgDelay = nil
        showVehicleDetail = false
        recompute()
    }

    private func loadDetails(id: String) async {
        if let r = try? await api.vehicleRoute(id: id), selectedVehicleId == id {
            route = r
            routeLoadedAt = Date()
        } else if selectedVehicleId == id, route == nil {
            route = ODVehicleRoute.unavailable
            routeLoadedAt = Date()
        }
        if Task.isCancelled { return }
        if let i = try? await api.vehicleInfo(id: id), selectedVehicleId == id {
            info = i
            if i.pending {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                if !Task.isCancelled, selectedVehicleId == id,
                   let again = try? await api.vehicleInfo(id: id, fresh: true) {
                    info = again
                }
            }
        }
        if Task.isCancelled { return }
        if let v = selectedVehicle, v.id == id, let trip = v.tripId, !trip.isEmpty,
           let avg = try? await api.averageDelay(provider: v.provider, line: v.line, destination: v.destination, tripId: trip),
           selectedVehicleId == id {
            avgDelay = avg
        }
    }

    private func refreshRouteIfStale() async {
        guard let id = selectedVehicleId else { return }
        if let at = routeLoadedAt, Date().timeIntervalSince(at) < 20 { return }
        routeLoadedAt = Date()
        if let r = try? await api.vehicleRoute(id: id), selectedVehicleId == id {
            route = r
        }
    }

    // MARK: Navigace z jiných záložek

    func focus(vehicle: ODVehicle) {
        selectVehicle(vehicle)
        recenter = ODRecenter(center: vehicle.coordinate, meters: 1800)
    }

    func focus(stop: ODStop) {
        recenter = ODRecenter(center: stop.coordinate, meters: 900)
        openStop = stop
    }

    func center(on coordinate: CLLocationCoordinate2D, meters: Double = 1500) {
        recenter = ODRecenter(center: coordinate, meters: meters)
    }
}
