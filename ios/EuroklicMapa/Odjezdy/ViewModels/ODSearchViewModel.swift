import CoreLocation
import Foundation
import SwiftUI

struct ODNearbyStop: Identifiable, Hashable {
    let stop: ODStop
    let distanceM: Int
    var id: String { stop.id }
}

@MainActor
final class ODSearchViewModel: ObservableObject {
    @Published var query = ""
    @Published private(set) var stops: [ODStop] = []
    @Published private(set) var vehicles: [ODVehicle] = []
    @Published private(set) var nearby: [ODNearbyStop] = []
    @Published private(set) var isSearching = false
    @Published private(set) var errorText: String?
    @Published private(set) var hasSearched = false

    private var searchTask: Task<Void, Never>?
    private let api = ODAPIClient.shared

    nonisolated init() {}

    var trimmedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Volat při každé změně textu (debounce 350 ms, předchozí hledání se zruší).
    func queryChanged() {
        searchTask?.cancel()
        let q = trimmedQuery
        guard q.count >= 2 else {
            stops = []
            vehicles = []
            errorText = nil
            hasSearched = false
            isSearching = false
            return
        }
        isSearching = true
        searchTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled, let self else { return }
            await self.search(q)
        }
    }

    private func search(_ q: String) async {
        do {
            async let vehicleResult: [ODVehicle] = Self.safeVehicles(api: api, query: q)
            let found = try await api.searchStops(query: q)
            let vs = await vehicleResult
            if Task.isCancelled { return }
            stops = Self.rank(found, query: q)
            vehicles = Array(vs.prefix(20))
            errorText = nil
        } catch is CancellationError {
            return
        } catch let error as ODAPIError {
            errorText = error.errorDescription
            stops = []
            vehicles = []
        } catch {
            errorText = "Hledání se nepodařilo."
        }
        hasSearched = true
        isSearching = false
    }

    private static func safeVehicles(api: ODAPIClient, query: String) async -> [ODVehicle] {
        guard query.count >= 3 else { return [] }
        return (try? await api.searchVehicles(query: query)) ?? []
    }

    /// Nejdřív názvy začínající hledaným textem, potom ostatní, abecedně.
    static func rank(_ stops: [ODStop], query: String) -> [ODStop] {
        let q = query.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "cs_CZ"))
        func score(_ s: ODStop) -> Int {
            let n = s.name.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "cs_CZ"))
            if n == q { return 0 }
            if n.hasPrefix(q) { return 1 }
            return 2
        }
        return stops.sorted { a, b in
            let sa = score(a)
            let sb = score(b)
            if sa != sb { return sa < sb }
            return a.name.localizedCompare(b.name) == .orderedAscending
        }
    }

    /// Nejbližší zastávky kolem polohy (pro prázdné hledání).
    func loadNearby(around location: CLLocation) async {
        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude
        do {
            let list = try await api.stops(
                minLat: lat - 0.008, maxLat: lat + 0.008,
                minLng: lng - 0.012, maxLng: lng + 0.012
            )
            var seen = Set<String>()
            let sorted = list
                .map { ODNearbyStop(stop: $0, distanceM: Int(location.distance(from: CLLocation(latitude: $0.lat, longitude: $0.lng)))) }
                .sorted { $0.distanceM < $1.distanceM }
                .filter { seen.insert($0.stop.name).inserted }
            nearby = Array(sorted.prefix(10))
        } catch {
            // V okolí je doplněk; bez něj prostě sekce chybí.
        }
    }
}
