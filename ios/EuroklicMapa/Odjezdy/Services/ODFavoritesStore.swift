import CoreLocation
import Foundation
import SwiftUI

/// Oblíbené zastávky režimu Odjezdy: lokálně, JSON v Documents, nejvýš 50 kusů.
/// (Odděleno od oblíbených toalet; nic se neposílá na server.)
final class ODFavoritesStore: ObservableObject {
    static let maxCount = 50

    @Published private(set) var stops: [ODStop] = []

    private let fileURL: URL
    private var importObserver: NSObjectProtocol?

    static func url(for fileName: String) -> URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return dir.appendingPathComponent(fileName)
    }

    /// Soubor výchozího úložiště (čte a zapisuje ho i přenos dat, `DataTransfer`).
    static var defaultFileURL: URL { url(for: "od_favorite_stops.json") }

    init(fileName: String = "od_favorite_stops.json") {
        fileURL = Self.url(for: fileName)
        load()
        importObserver = NotificationCenter.default.addObserver(
            forName: DataTransfer.didImport, object: nil, queue: .main
        ) { [weak self] _ in
            self?.load()
        }
    }

    deinit {
        if let importObserver { NotificationCenter.default.removeObserver(importObserver) }
    }

    func contains(_ stop: ODStop) -> Bool {
        stops.contains { $0.id == stop.id }
    }

    /// `false`, když je plno (50).
    @discardableResult
    func add(_ stop: ODStop) -> Bool {
        guard !contains(stop) else { return true }
        guard stops.count < Self.maxCount else { return false }
        stops.insert(stop, at: 0)
        save()
        return true
    }

    func remove(_ stop: ODStop) {
        stops.removeAll { $0.id == stop.id }
        save()
    }

    func remove(atOffsets offsets: IndexSet) {
        stops.remove(atOffsets: offsets)
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        stops.move(fromOffsets: source, toOffset: destination)
        save()
    }

    /// Přidá, nebo odebere. Vrací `false`, jen když se nepodařilo přidat kvůli limitu.
    @discardableResult
    func toggle(_ stop: ODStop) -> Bool {
        if contains(stop) {
            remove(stop)
            return true
        }
        return add(stop)
    }

    // MARK: Oprava názvů (server měl u části zastávek cizí název, 2026-11-27)

    private static let namesCheckedKey = "od_fav_names_checked_v1"

    /// Nový název jen pro stejné ID a stejné místo (do 200 m), jinak `nil` – nic nedomýšlíme.
    static func repaired(_ stop: ODStop, candidates: [ODStop]) -> ODStop? {
        guard let match = candidates.first(where: { $0.id == stop.id }),
              !match.name.isEmpty, match.name != stop.name else { return nil }
        let distance = CLLocation(latitude: stop.lat, longitude: stop.lng)
            .distance(from: CLLocation(latitude: match.lat, longitude: match.lng))
        guard distance <= 200 else { return nil }
        return ODStop(id: stop.id, name: match.name, lat: stop.lat, lng: stop.lng, provider: stop.provider)
    }

    /// Jednorázově po aktualizaci porovná uložené názvy se serverem. Při chybě to zkusí příště.
    @MainActor
    func refreshNamesIfNeeded(api: ODAPIClient = .shared) async {
        guard !UserDefaults.standard.bool(forKey: Self.namesCheckedKey), !stops.isEmpty else { return }
        for stop in stops {
            do {
                let around = try await api.stops(
                    minLat: stop.lat - 0.002, maxLat: stop.lat + 0.002,
                    minLng: stop.lng - 0.003, maxLng: stop.lng + 0.003
                )
                if let fixed = Self.repaired(stop, candidates: around),
                   let index = stops.firstIndex(where: { $0.id == stop.id }) {
                    stops[index] = fixed
                    save()
                }
            } catch {
                return
            }
        }
        UserDefaults.standard.set(true, forKey: Self.namesCheckedKey)
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let list = try? JSONDecoder().decode([ODStop].self, from: data) else { return }
        stops = Array(list.prefix(Self.maxCount))
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(stops) else { return }
        try? data.write(to: fileURL, options: [.atomic])
    }
}
