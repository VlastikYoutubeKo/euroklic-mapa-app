import MapKit
import SwiftUI

/// MKMapView wrapper: Mapy.com raster tiles, builtin clustering, selection.
/// (Android hand-rolls grid clustering below z13.5; here the built-in
/// `MKMarkerAnnotationView` clustering is good enough for SLC.)
struct MapView: UIViewRepresentable {
    let items: [PlaceItem]
    var selectedKey: String?
    var userLocation: CLLocation?
    var provider: MapProvider = .mapy
    /// Set by the address search: the map flies there once per distinct target.
    var recenterTarget: CLLocationCoordinate2D?
    /// Receives the visible area (for "Stáhnout mapu"); optional.
    var viewport: MapViewport?
    /// Pěší trasa k vybranému místu (kreslí se přes dlaždice, pod značkami).
    var route: MKPolyline?
    var onSelect: (PlaceItem?) -> Void

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsUserLocation = true
        map.preferredConfiguration = MKStandardMapConfiguration()

        map.applyBasemap(provider, attribution: true)
        context.coordinator.appliedProvider = provider

        // Start on CZ/SK (MKMapView would open on the whole world); the first
        // location fix re-centres on the user in updateUIView.
        map.setRegion(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 49.8, longitude: 15.5),
            span: MKCoordinateSpan(latitudeDelta: 5, longitudeDelta: 6),
        ), animated: false)

        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.parent = self
        if !context.coordinator.didCenterOnUser, let userLocation {
            context.coordinator.didCenterOnUser = true
            map.setRegion(MKCoordinateRegion(
                center: userLocation.coordinate,
                latitudinalMeters: 2500,
                longitudinalMeters: 2000,
            ), animated: false)
        }
        if context.coordinator.appliedProvider != provider {
            map.applyBasemap(provider, attribution: true)
            context.coordinator.appliedProvider = provider
        }

        if let t = recenterTarget {
            let key = "\(t.latitude),\(t.longitude)"
            if context.coordinator.lastRecenterKey != key {
                context.coordinator.lastRecenterKey = key
                map.setRegion(MKCoordinateRegion(center: t, latitudinalMeters: 1500, longitudinalMeters: 1500), animated: true)
            }
        } else {
            context.coordinator.lastRecenterKey = nil
        }

        // Route line: replace only our own polylines, never the tile overlay.
        if context.coordinator.lastRoute !== route {
            map.removeOverlays(map.overlays.filter { $0 is MKPolyline })
            context.coordinator.lastRoute = route
            if let route {
                map.addOverlay(route, level: .aboveRoads)
                // The detail sheet covers the lower part of the screen: put the whole line into the free band above it.
                map.setVisibleMapRect(Self.rect(showing: route.boundingMapRect, in: map.bounds.size), animated: true)
            }
        }

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
                center: CLLocationCoordinate2D(
                    latitude: annotation.coordinate.latitude - 0.00135,
                    longitude: annotation.coordinate.longitude,
                ),
                latitudinalMeters: 700,
                longitudinalMeters: 500,
            )
            map.setRegion(region, animated: true)
        }
        if selectedKey == nil { context.coordinator.lastSelectedKey = nil }
    }

    /// Podíl výšky, který zabere sheet s detailem místa (musí sedět s `presentationDetents` v MapScreen).
    static let routeSheetFraction: CGFloat = 0.58

    /// Výřez mapy, ve kterém je celá trasa ve volném pásu nad sheetem (pod vyhledáváním).
    static func rect(showing route: MKMapRect, in size: CGSize) -> MKMapRect {
        guard size.width > 0, size.height > 0, route.size.width + route.size.height > 0 else { return route }
        let topReserved: CGFloat = 0.16
        let free = 1 - routeSheetFraction - topReserved
        let aspect = size.width / size.height
        // Výška výřezu tak, aby trasa zabrala ~85 % volného pásu na výšku a ~80 % na šířku.
        let fromHeight = route.size.height / Double(free * 0.85)
        let fromWidth = route.size.width / 0.8 / Double(aspect)
        let minHeight = MKMapPointsPerMeterAtLatitude(MKMapPoint(x: route.midX, y: route.midY).coordinate.latitude) * 700
        let height = max(fromHeight, fromWidth, minHeight)
        let width = height * Double(aspect)
        // Střed trasy má být uprostřed volného pásu, ne uprostřed obrazovky.
        let bandCenter = Double(topReserved + free / 2)
        let centerY = route.midY + height * (0.5 - bandCenter)
        return MKMapRect(x: route.midX - width / 2, y: centerY - height / 2, width: width, height: height)
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var parent: MapView
        var lastSelectedKey: String?
        var appliedProvider: MapProvider?
        var didCenterOnUser = false
        var lastRecenterKey: String?
        var lastRoute: MKPolyline?

        init(parent: MapView) { self.parent = parent }

        func mapViewDidChangeVisibleRegion(_ mapView: MKMapView) {
            parent.viewport?.rect = mapView.visibleMapRect
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tiles = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tiles)
            }
            if let line = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: line)
                renderer.strokeColor = UIColor(Theme.brand)
                renderer.lineWidth = 6
                renderer.lineCap = .round
                renderer.lineJoin = .round
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }

            if let cluster = annotation as? MKClusterAnnotation {
                let view = (mapView.dequeueReusableAnnotationView(withIdentifier: "cluster") as? MKMarkerAnnotationView)
                    ?? MKMarkerAnnotationView(annotation: cluster, reuseIdentifier: "cluster")
                view.glyphText = String(cluster.memberAnnotations.count)
                view.titleVisibility = .hidden
                view.subtitleVisibility = .hidden
                view.markerTintColor = UIColor(Theme.brand)
                view.displayPriority = .defaultHigh
                return view
            }

            guard let placeAnnotation = annotation as? PlaceAnnotation else { return nil }
            let place = placeAnnotation.place
            let identifier = "place-\(place.isPickup)-\(place.status == .reported)"
            let view = (mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView)
                ?? MKMarkerAnnotationView(annotation: placeAnnotation, reuseIdentifier: identifier)
            view.annotation = placeAnnotation
            view.clusteringIdentifier = "place"
            view.displayPriority = .defaultHigh
            view.canShowCallout = false

            if place.isPickup {
                view.markerTintColor = UIColor(Theme.pickup)
                view.glyphImage = UIImage(systemName: "shippingbox.fill")
            } else {
                view.markerTintColor = UIColor(place.isOfficialSource ? Theme.official : Theme.community)
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
                region.span.latitudeDelta /= 4
                region.span.longitudeDelta /= 4
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
