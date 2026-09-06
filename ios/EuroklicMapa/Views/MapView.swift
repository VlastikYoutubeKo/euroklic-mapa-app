import MapKit
import SwiftUI

/// MKMapView wrapper: Mapy.com raster tiles, builtin clustering, selection.
/// (Android hand-rolls grid clustering below z13.5; here the built-in
/// `MKMarkerAnnotationView` clustering is good enough for SLC.)
struct MapView: UIViewRepresentable {
    let items: [PlaceItem]
    var selectedKey: String?
    var userLocation: CLLocation?
    var onSelect: (PlaceItem?) -> Void

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsUserLocation = true
        map.preferredConfiguration = MKStandardMapConfiguration()

        // Mapy.com raster tiles when the key was injected at build time
        // (Secrets.xcconfig); otherwise fall back to Apple's basemap.
        if !MapyTiles.urlTemplate.isEmpty {
            let tiles = MKTileOverlay(urlTemplate: MapyTiles.urlTemplate)
            tiles.tileSize = CGSize(width: 256, height: 256)
            tiles.canReplaceMapContent = true // raster tiles replace Apple's basemap
            map.addOverlay(tiles, level: .aboveLabels)

            // Mandatory Mapy.com attribution (only when their tiles are shown).
            let attribution = UILabel()
            attribution.text = " © Seznam.cz a.s. a další"
            attribution.font = .systemFont(ofSize: 10)
            attribution.textColor = .darkGray
            attribution.backgroundColor = .systemBackground.withAlphaComponent(0.7)
            attribution.sizeToFit()
            attribution.translatesAutoresizingMaskIntoConstraints = false
            map.addSubview(attribution)
            NSLayoutConstraint.activate([
                attribution.leadingAnchor.constraint(equalTo: map.safeAreaLayoutGuide.leadingAnchor, constant: 6),
                attribution.bottomAnchor.constraint(equalTo: map.safeAreaLayoutGuide.bottomAnchor, constant: -2),
            ])
        } else {
            print("⚠️ MapyTiles: MAPY_APIKEY chybí — mapy padají na Apple basemap. Vytvoř ios/Secrets.xcconfig (viz README).")
        }

        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.parent = self

        // Diff annotations (keep the tile overlay + user location).
        let desired = Dictionary(uniqueKeysWithValues: items.map { ($0.favoriteKey, $0) })
        let current = map.annotations.compactMap { $0 as? PlaceAnnotation }
        let currentKeys = Set(current.map(\.place.favoriteKey))

        let toRemove = current.filter { desired[$0.place.favoriteKey] == nil }
        let toAdd = items.filter { !currentKeys.contains($0.favoriteKey) }
        map.removeAnnotations(toRemove)
        map.addAnnotations(toAdd.map(PlaceAnnotation.init))

        // Re-render changed trust states (recomputed per render, never cached).
        for annotation in current {
            if let updated = desired[annotation.place.favoriteKey] {
                annotation.place = updated
            }
        }

        // Follow the selection once (camera moves so the card doesn't cover the pin).
        if let selectedKey,
           let annotation = current.first(where: { $0.place.favoriteKey == selectedKey }),
           context.coordinator.lastSelectedKey != selectedKey {
            context.coordinator.lastSelectedKey = selectedKey
            let region = MKCoordinateRegion(
                center: annotation.coordinate,
                latitudinalMeters: 700,
                longitudinalMeters: 500,
            )
            map.setRegion(region, animated: true)
        }
        if selectedKey == nil { context.coordinator.lastSelectedKey = nil }
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var parent: MapView
        var lastSelectedKey: String?

        init(parent: MapView) { self.parent = parent }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tiles = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tiles)
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }

            if let cluster = annotation as? MKClusterAnnotation {
                let view = (mapView.dequeueReusableAnnotationView(withIdentifier: "cluster", for: cluster) as? MKMarkerAnnotationView)
                    ?? MKMarkerAnnotationView(annotation: cluster, reuseIdentifier: "cluster")
                view.glyphText = String(cluster.memberAnnotations.count)
                view.markerTintColor = Theme.brand
                view.displayPriority = .defaultHigh
                return view
            }

            guard let placeAnnotation = annotation as? PlaceAnnotation else { return nil }
            let place = placeAnnotation.place
            let identifier = "place-\(place.isPickup)-\(place.status == .reported)"
            let view = (mapView.dequeueReusableAnnotationView(withIdentifier: identifier, for: placeAnnotation) as? MKMarkerAnnotationView)
                ?? MKMarkerAnnotationView(annotation: placeAnnotation, reuseIdentifier: identifier)
            view.annotation = placeAnnotation
            view.clusteringIdentifier = "place"
            view.displayPriority = .defaultHigh
            view.canShowCallout = false

            if place.isPickup {
                view.markerTintColor = Theme.pickup
                view.glyphImage = UIImage(systemName: "shippingbox.fill")
            } else {
                view.markerTintColor = place.isOfficialSource ? Theme.official : Theme.community
                view.glyphImage = UIImage(systemName: "figure.stand")
            }
            if place.status == .reported {
                view.alpha = 0.45 // "hollow" approximation
                view.glyphImage = UIImage(systemName: "exclamationmark.triangle.fill")
            } else {
                view.alpha = 1
            }
            return view
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            if let cluster = view.annotation as? MKClusterAnnotation {
                // Tapping a cluster drills in (Android zooms in +2 too).
                var region = mapView.region
                region.span.latitudeSpan /= 4
                region.span.longitudeSpan /= 4
                region.center = cluster.coordinate
                mapView.setRegion(region, animated: true)
                mapView.deselectAnnotation(cluster, animated: false)
                return
            }
            if let placeAnnotation = view.annotation as? PlaceAnnotation {
                parent.onSelect(placeAnnotation.place)
            }
        }

        func mapView(_ mapView: MKMapView, didDeselect view: MKAnnotationView) {
            if view.annotation is PlaceAnnotation {
                parent.onSelect(nil)
            }
        }
    }
}

final class PlaceAnnotation: MKPointAnnotation {
    var place: PlaceItem {
        didSet {
            coordinate = place.coordinate
            title = place.name
            subtitle = place.badgeLabel
        }
    }

    init(place: PlaceItem) {
        self.place = place
        super.init()
        coordinate = place.coordinate
        title = place.name
        subtitle = place.badgeLabel
    }
}
