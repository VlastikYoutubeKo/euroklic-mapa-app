import Foundation

/// Favorites are USER DATA, not a disposable cache — same principle as Android
/// DB v4: persisted independently of the feed cache, survives refreshes.
@MainActor
final class FavoritesStore: ObservableObject {
    @Published private(set) var items: [PlaceItem] = []

    nonisolated static let fileURL: URL = {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("favorites.json")
    }()

    private var fileURL: URL { Self.fileURL }
    private var importObserver: NSObjectProtocol?

    init() {
        load()
        // Přenos dat (DataTransfer) zapíše soubor přímo; tady se jen znovu načte.
        importObserver = NotificationCenter.default.addObserver(
            forName: DataTransfer.didImport, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.load() }
        }
    }

    deinit {
        if let importObserver { NotificationCenter.default.removeObserver(importObserver) }
    }

    private func load() {
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode([PlaceItem].self, from: data) {
            items = loaded
        }
    }

    func isFavorite(_ item: PlaceItem) -> Bool {
        items.contains { $0.favoriteKey == item.favoriteKey }
    }

    func toggle(_ item: PlaceItem) {
        if isFavorite(item) {
            items.removeAll { $0.favoriteKey == item.favoriteKey }
        } else {
            items.append(item)
        }
        persist()
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(items) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}
