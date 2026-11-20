import MapKit
import Network
import SwiftUI

/// Mapy.com tile overlay backed by a persistent on-disk cache (Application Support, so iOS never
/// evicts it like `Caches`). Every tile shown is kept; the "Stáhnout mapu" button pre-fills the
/// cache for the visible area. Offline, the map simply reads the files — no network needed.
final class CachedTileOverlay: MKTileOverlay {
    /// Tiles older than this are refreshed when online (and still served from disk if offline).
    private static let maxAge: TimeInterval = 30 * 24 * 3600

    override func loadTile(at path: MKTileOverlayPath, result: @escaping (Data?, Error?) -> Void) {
        let file = OfflineTiles.file(for: path)
        let cached = try? Data(contentsOf: file)
        if let cached, !OfflineTiles.isStale(file, maxAge: Self.maxAge) {
            result(cached, nil)
            return
        }
        URLSession.shared.dataTask(with: url(forTilePath: path)) { data, response, error in
            if let data, (response as? HTTPURLResponse)?.statusCode == 200 {
                OfflineTiles.store(data, at: file)
                result(data, nil)
            } else {
                // Offline or server trouble: an old tile beats a blank one.
                result(cached, cached == nil ? (error ?? URLError(.badServerResponse)) : nil)
            }
        }.resume()
    }
}

enum OfflineTiles {
    /// Same cap as Android: refuse accidental country-wide scrapes of the tile server.
    static let maxTiles = 3000

    enum Outcome {
        case done(downloaded: Int)
        case tooLarge(count: Int)
        case failed
        case unavailable // no Mapy.com key / Apple Maps selected
    }

    static let directory: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("tiles", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true // re-downloadable, keep iCloud backups small
        var mutableDir = dir
        try? mutableDir.setResourceValues(values)
        return dir
    }()

    static func file(for path: MKTileOverlayPath) -> URL {
        directory
            .appendingPathComponent("\(path.z)", isDirectory: true)
            .appendingPathComponent("\(path.x)", isDirectory: true)
            .appendingPathComponent("\(path.y).png")
    }

    static func store(_ data: Data, at file: URL) {
        try? FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: file, options: .atomic)
    }

    static func isStale(_ file: URL, maxAge: TimeInterval) -> Bool {
        guard let date = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate else { return true }
        return Date().timeIntervalSince(date) > maxAge
    }

    /// Tile coordinates covering [rect] for each zoom level (web-mercator = MapKit's map-point space).
    static func tiles(in rect: MKMapRect, zooms: ClosedRange<Int>) -> [MKTileOverlayPath] {
        var out: [MKTileOverlayPath] = []
        for z in zooms {
            let n = 1 << z
            let scale = Double(n) / MKMapSize.world.width
            let x0 = max(0, Int(rect.minX * scale)), x1 = min(n - 1, Int(rect.maxX * scale))
            let y0 = max(0, Int(rect.minY * scale)), y1 = min(n - 1, Int(rect.maxY * scale))
            guard x0 <= x1, y0 <= y1 else { continue }
            for x in x0...x1 {
                for y in y0...y1 { out.append(MKTileOverlayPath(x: x, y: y, z: z, contentScaleFactor: 1)) }
            }
        }
        return out
    }

    /// Downloads the visible area from its current zoom up to +3 levels (city → street level).
    /// [progress] is called on the main actor with 0…1.
    static func prefetch(visible rect: MKMapRect, progress: @escaping @MainActor (Double) -> Void) async -> Outcome {
        guard !MapyTiles.urlTemplate.isEmpty else { return .unavailable }
        let zoomNow = max(4, min(17, Int(log2(MKMapSize.world.width / max(rect.size.width, 1)) + 0.5)))
        let zooms = zoomNow...min(17, zoomNow + 3)
        let all = tiles(in: rect, zooms: zooms)
        if all.count > maxTiles { return .tooLarge(count: all.count) }

        let overlay = MKTileOverlay(urlTemplate: MapyTiles.urlTemplate)
        let todo = all.filter { !FileManager.default.fileExists(atPath: file(for: $0).path) }
        if todo.isEmpty { return .done(downloaded: 0) }

        var finished = 0
        var failures = 0
        await withTaskGroup(of: Bool.self) { group in
            var iterator = todo.makeIterator()
            func next() {
                guard let path = iterator.next() else { return }
                let url = overlay.url(forTilePath: path)
                group.addTask {
                    guard let (data, response) = try? await URLSession.shared.data(from: url),
                          (response as? HTTPURLResponse)?.statusCode == 200 else { return false }
                    store(data, at: file(for: path))
                    return true
                }
            }
            for _ in 0..<6 { next() } // 6 parallel requests, like Android's 8 download threads
            for await ok in group {
                finished += 1
                if !ok { failures += 1 }
                let fraction = Double(finished) / Double(todo.count)
                await progress(fraction)
                next()
            }
        }
        // Mostly failed = effectively offline / blocked.
        return failures > todo.count / 4 ? .failed : .done(downloaded: todo.count - failures)
    }

    static var sizeOnDisk: Int64 {
        guard let e = FileManager.default.enumerator(at: directory, includingPropertiesForKeys: [.fileSizeKey]) else { return 0 }
        var total: Int64 = 0
        for case let url as URL in e { total += Int64((try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0) }
        return total
    }

    static func clear() {
        try? FileManager.default.removeItem(at: directory)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }
}

/// Reachability for the offline-mode pill.
@MainActor
final class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()
    @Published private(set) var isOnline = true
    private let monitor = NWPathMonitor()

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in self?.isOnline = online }
        }
        monitor.start(queue: DispatchQueue(label: "net.monitor"))
    }
}

/// Visible map area, written by `MapView`'s coordinator so the SwiftUI screen can ask
/// "download this area" without owning the MKMapView.
final class MapViewport: ObservableObject {
    var rect: MKMapRect = .null
}
