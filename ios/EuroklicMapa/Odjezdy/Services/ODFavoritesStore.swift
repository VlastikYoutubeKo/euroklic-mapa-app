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
