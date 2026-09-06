import Foundation

/// Favorites are USER DATA, not a disposable cache — same principle as Android
/// DB v4: persisted independently of the feed cache, survives refreshes.
@MainActor
final class FavoritesStore: ObservableObject {
    @Published private(set) var items: [PlaceItem] = []

    private let fileURL: URL = {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("favorites.json")
    }()

    init() {
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
