import MapKit
import SwiftUI
import UIKit

/// Which basemap the user prefers. Persisted under `map_provider`
/// (`@AppStorage` in SwiftUI, `UserDefaults` in the UIKit map wrappers).
enum MapProvider: String, CaseIterable, Identifiable {
    case mapy
    case apple

    static let storageKey = "map_provider"
    var id: String { rawValue }

    var label: String {
        switch self {
        case .mapy: return "Mapy.com"
        case .apple: return "Apple Maps"
        }
    }

    /// Current choice for UIKit wrappers that are rebuilt per screen.
    static var stored: MapProvider {
        MapProvider(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .mapy
    }
}

private let attributionTag = 7788

extension MKMapView {
    /// Switches the basemap: Mapy.com raster tiles (needs the build-time key)
    /// or Apple's own. Safe to call repeatedly; the Mapy.com attribution is
    /// shown only while their tiles are.
    func applyBasemap(_ provider: MapProvider, attribution: Bool = false) {
        overlays.compactMap { $0 as? MKTileOverlay }.forEach { removeOverlay($0) }
        viewWithTag(attributionTag)?.removeFromSuperview()

        guard provider == .mapy, !MapyTiles.urlTemplate.isEmpty else { return }
        let tiles = CachedTileOverlay(urlTemplate: MapyTiles.urlTemplate)
        tiles.tileSize = CGSize(width: 256, height: 256)
        tiles.canReplaceMapContent = true // raster tiles replace Apple's basemap
        addOverlay(tiles, level: .aboveLabels)

        guard attribution else { return }
        // Mandatory Mapy.com attribution.
        let label = UILabel()
        label.tag = attributionTag
        label.text = L(" © Seznam.cz a.s. a další")
        label.font = .systemFont(ofSize: 10)
        label.textColor = .darkGray
        label.backgroundColor = .systemBackground.withAlphaComponent(0.7)
        label.sizeToFit()
        label.translatesAutoresizingMaskIntoConstraints = false
        addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor, constant: 6),
            label.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -2),
        ])
    }
}
