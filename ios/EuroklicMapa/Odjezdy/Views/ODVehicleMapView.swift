import MapKit
import SwiftUI
import UIKit

// MARK: - Anotace

final class ODVehicleAnnotation: NSObject, MKAnnotation {
    let vehicleId: String
    @objc dynamic var coordinate: CLLocationCoordinate2D
    var vehicle: ODVehicle
    /// Kdy byla poloha `vehicle` načtena (od toho se dopočítává pohyb).
    var baseTime: Date

    var title: String? { vehicle.badgeText }

    init(vehicle: ODVehicle, baseTime: Date) {
        self.vehicleId = vehicle.id
        self.vehicle = vehicle
        self.baseTime = baseTime
        self.coordinate = vehicle.coordinate
        super.init()
    }
}

final class ODStopAnnotation: NSObject, MKAnnotation {
    let stop: ODStop
    let coordinate: CLLocationCoordinate2D

    var title: String? { stop.name }

    init(stop: ODStop) {
        self.stop = stop
        self.coordinate = stop.coordinate
        super.init()
    }
}

final class ODRoutePolyline: MKPolyline {
    var isTraveled = false
}

// MARK: - Pohledy anotací

final class ODVehicleView: MKAnnotationView {
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        canShowCallout = false
        collisionMode = .rectangle
        isAccessibilityElement = true
        accessibilityTraits = .button
    }

    @available(*, unavailable)
    required init?(coder aDecoder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

final class ODStopView: MKAnnotationView {
    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        canShowCallout = false
        clusteringIdentifier = "od-stop"
        collisionMode = .circle
        displayPriority = .defaultLow
        isAccessibilityElement = true
        accessibilityTraits = .button
    }

    @available(*, unavailable)
    required init?(coder aDecoder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

// MARK: - Kreslení štítků

enum ODMapImages {
    static func labelFont(_ category: UIContentSizeCategory) -> UIFont {
        let base = UIFont.systemFont(ofSize: 13, weight: .bold)
        let traits: [UIFontDescriptor.TraitKey: Any] = [
            .width: -0.3,
            .weight: UIFont.Weight.bold.rawValue
        ]
        let desc = base.fontDescriptor.addingAttributes([.traits: traits])
        let font = UIFont(descriptor: desc, size: 13)
        return UIFontMetrics(forTextStyle: .caption1).scaledFont(
            for: font,
            maximumPointSize: 26,
            compatibleWith: UITraitCollection(preferredContentSizeCategory: category)
        )
    }

    /// Štítek vozidla s číslem linky (barva podle typu, bílý rámeček, vybrané = žlutý rámeček).
    static func vehicleLabel(text: String, fill: UIColor, selected: Bool, category: UIContentSizeCategory) -> UIImage {
        let font = labelFont(category)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
        let textSize = (text as NSString).size(withAttributes: attrs)
        let padX: CGFloat = 6
        let padY: CGFloat = 2.5
        let border: CGFloat = selected ? 3 : 2
        let innerW = max(textSize.width + padX * 2, 26)
        let innerH = textSize.height + padY * 2
        let size = CGSize(width: ceil(innerW + border * 2), height: ceil(innerH + border * 2))
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            let outerRect = CGRect(origin: .zero, size: size)
            let outer = UIBezierPath(roundedRect: outerRect, cornerRadius: 7)
            (selected ? UIColor(odHex: 0xffd400) : UIColor.white).setFill()
            outer.fill()
            let innerRect = outerRect.insetBy(dx: border, dy: border)
            let inner = UIBezierPath(roundedRect: innerRect, cornerRadius: 5)
            fill.setFill()
            inner.fill()
            let textRect = CGRect(
                x: innerRect.minX + (innerRect.width - textSize.width) / 2,
                y: innerRect.minY + (innerRect.height - textSize.height) / 2,
                width: textSize.width,
                height: textSize.height
            )
            (text as NSString).draw(in: textRect, withAttributes: attrs)
            _ = ctx
        }
    }

    /// Bod zastávky (bílý střed, barevný kroužek jako `.pin-stop` na webu).
    static func stopDot(ring: UIColor, category: UIContentSizeCategory) -> UIImage {
        let scale = UIFontMetrics(forTextStyle: .caption1).scaledValue(
            for: 20,
            compatibleWith: UITraitCollection(preferredContentSizeCategory: category)
        )
        let d = min(max(scale, 20), 34)
        let size = CGSize(width: d, height: d)
        return UIGraphicsImageRenderer(size: size).image { _ in
            let rect = CGRect(origin: .zero, size: size)
            UIColor.white.setFill()
            UIBezierPath(ovalIn: rect).fill()
            ring.setFill()
            UIBezierPath(ovalIn: rect.insetBy(dx: 2, dy: 2)).fill()
            UIColor.white.setFill()
            UIBezierPath(ovalIn: rect.insetBy(dx: d * 0.3, dy: d * 0.3)).fill()
        }
    }
}

// MARK: - Mapa

struct ODVehicleMapView: UIViewRepresentable {
    let vehicles: [ODVehicle]
    let fetchedAt: Date?
    let stops: [ODStop]
    let selectedVehicleId: String?
    let route: ODVehicleRoute?
    let recenter: ODRecenter?
    let userLocation: CLLocation?
    let showsUserLocation: Bool
    let provider: MapProvider
    let isDark: Bool
    let signColor: UInt32
    let routeColor: UInt32
    var onRegion: (MKCoordinateRegion) -> Void
    var onSelectVehicle: (ODVehicle) -> Void
    var onSelectStop: (ODStop) -> Void
    var onTapEmpty: () -> Void

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsUserLocation = showsUserLocation
        map.isRotateEnabled = false
        map.isPitchEnabled = false
        map.preferredConfiguration = MKStandardMapConfiguration()
        map.pointOfInterestFilter = MKPointOfInterestFilter.excludingAll
        map.register(ODVehicleView.self, forAnnotationViewWithReuseIdentifier: "od-veh")
        map.register(ODStopView.self, forAnnotationViewWithReuseIdentifier: "od-stop")
        map.applyBasemap(provider, attribution: true)
        context.coordinator.appliedProvider = provider

        // Výchozí pohled: Česko (první poloha ho přesune na uživatele).
        map.setRegion(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 49.8, longitude: 15.5),
            span: MKCoordinateSpan(latitudeDelta: 4.5, longitudeDelta: 6)
        ), animated: false)

        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        tap.cancelsTouchesInView = false
        tap.delegate = context.coordinator
        map.addGestureRecognizer(tap)

        context.coordinator.startTimer(map)
        return map
    }

    static func dismantleUIView(_ uiView: MKMapView, coordinator: Coordinator) {
        coordinator.stopTimer()
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        let co = context.coordinator
        co.parent = self

        if map.showsUserLocation != showsUserLocation {
            map.showsUserLocation = showsUserLocation
        }
        if co.appliedProvider != provider {
            map.applyBasemap(provider, attribution: true)
            co.appliedProvider = provider
        }
        if co.appliedDark != isDark {
            co.appliedDark = isDark
            co.imageCache.removeAll()
            co.refreshAllViews(map)
        }

        if let rc = recenter, rc.id != co.lastRecenterId {
            co.lastRecenterId = rc.id
            co.didCenterOnUser = true
            map.setRegion(MKCoordinateRegion(
                center: rc.center, latitudinalMeters: rc.meters, longitudinalMeters: rc.meters
            ), animated: true)
        } else if !co.didCenterOnUser, let userLocation {
            co.didCenterOnUser = true
            map.setRegion(MKCoordinateRegion(
                center: userLocation.coordinate, latitudinalMeters: 3000, longitudinalMeters: 3000
            ), animated: false)
        }

        co.syncVehicles(map, vehicles: vehicles, fetchedAt: fetchedAt, selectedId: selectedVehicleId)
        co.syncStops(map, stops: stops)
        co.syncRoute(map, selectedId: selectedVehicleId, route: route)
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    // MARK: Coordinator

    final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: ODVehicleMapView
        var appliedProvider: MapProvider?
        var appliedDark: Bool?
        var didCenterOnUser = false
        var lastRecenterId: UUID?
        var imageCache: [String: UIImage] = [:]

        private var vehicleAnns: [String: ODVehicleAnnotation] = [:]
        private var stopAnns: [String: ODStopAnnotation] = [:]
        private var lastSelectedId: String?
        private var routeKey: String?
        private var timer: Timer?

        init(parent: ODVehicleMapView) { self.parent = parent }

        // MARK: Vozidla

        func syncVehicles(_ map: MKMapView, vehicles: [ODVehicle], fetchedAt: Date?, selectedId: String?) {
            var dict: [String: ODVehicle] = [:]
            dict.reserveCapacity(vehicles.count)
            for v in vehicles { dict[v.id] = v }

            let goneIds = vehicleAnns.keys.filter { dict[$0] == nil }
            let removed: [ODVehicleAnnotation] = goneIds.compactMap { vehicleAnns.removeValue(forKey: $0) }
            if !removed.isEmpty { map.removeAnnotations(removed) }

            let base = fetchedAt ?? Date()
            var added: [ODVehicleAnnotation] = []
            for (id, v) in dict {
                if let ann = vehicleAnns[id] {
                    if ann.vehicle != v {
                        ann.vehicle = v
                        ann.baseTime = base
                        let target = v.coordinate
                        UIView.animate(withDuration: 0.8, delay: 0, options: [.curveLinear, .allowUserInteraction]) {
                            ann.coordinate = target
                        }
                        configureView(map.view(for: ann) as? ODVehicleView, ann: ann, selectedId: selectedId)
                    }
                } else {
                    let ann = ODVehicleAnnotation(vehicle: v, baseTime: base)
                    vehicleAnns[id] = ann
                    added.append(ann)
                }
            }
            if !added.isEmpty { map.addAnnotations(added) }

            if selectedId != lastSelectedId {
                for id in [lastSelectedId, selectedId].compactMap({ $0 }) {
                    if let ann = vehicleAnns[id] {
                        configureView(map.view(for: ann) as? ODVehicleView, ann: ann, selectedId: selectedId)
                    }
                }
                lastSelectedId = selectedId
            }
        }

        func refreshAllViews(_ map: MKMapView) {
            for ann in vehicleAnns.values {
                configureView(map.view(for: ann) as? ODVehicleView, ann: ann, selectedId: lastSelectedId)
            }
            for ann in stopAnns.values {
                if let view = map.view(for: ann) as? ODStopView { configureStopView(view, map: map) }
            }
        }

        private func category(_ map: MKMapView) -> UIContentSizeCategory {
            map.traitCollection.preferredContentSizeCategory
        }

        func configureView(_ view: ODVehicleView?, ann: ODVehicleAnnotation, selectedId: String?) {
            guard let view else { return }
            let v = ann.vehicle
            let isSelected = v.id == selectedId
            let cat = view.traitCollection.preferredContentSizeCategory
            let stale: Bool = {
                guard let t = v.updatedAt else { return false }
                return Date().timeIntervalSince(t) > 180
            }()
            let key = "\(v.badgeText)|\(v.lineClass)|\(isSelected)|\(parent.isDark)|\(cat.rawValue)"
            let image: UIImage
            if let cached = imageCache[key] {
                image = cached
            } else {
                let fill = UIColor(odHex: ODTheme.lineHex(v.lineClass, dark: parent.isDark))
                image = ODMapImages.vehicleLabel(text: v.badgeText, fill: fill, selected: isSelected, category: cat)
                imageCache[key] = image
            }
            view.image = image
            view.alpha = (stale && !isSelected) ? 0.6 : 1
            view.displayPriority = isSelected ? .required : .defaultHigh
            view.accessibilityLabel = v.accessibilityText
            view.accessibilityValue = isSelected ? L("vybráno") : nil
            view.accessibilityHint = L("Zobrazí kartu vozidla")
            view.zPriority = isSelected ? MKAnnotationViewZPriority.max : MKAnnotationViewZPriority.defaultUnselected
        }

        // MARK: Zastávky

        func syncStops(_ map: MKMapView, stops: [ODStop]) {
            var dict: [String: ODStop] = [:]
            for s in stops { dict[s.id] = s }
            let goneIds = stopAnns.keys.filter { dict[$0] == nil }
            let removed: [ODStopAnnotation] = goneIds.compactMap { stopAnns.removeValue(forKey: $0) }
            if !removed.isEmpty { map.removeAnnotations(removed) }
            var added: [ODStopAnnotation] = []
            for (id, s) in dict where stopAnns[id] == nil {
                let ann = ODStopAnnotation(stop: s)
                stopAnns[id] = ann
                added.append(ann)
            }
            if !added.isEmpty { map.addAnnotations(added) }
        }

        func configureStopView(_ view: ODStopView, map: MKMapView) {
            let cat = map.traitCollection.preferredContentSizeCategory
            let key = "stop|\(parent.isDark)|\(cat.rawValue)"
            let image: UIImage
            if let cached = imageCache[key] {
                image = cached
            } else {
                image = ODMapImages.stopDot(ring: UIColor(odHex: parent.signColor), category: cat)
                imageCache[key] = image
            }
            view.image = image
            if let ann = view.annotation as? ODStopAnnotation {
                view.accessibilityLabel = L("Zastávka \(ann.stop.name)")
                view.accessibilityHint = L("Zobrazí odjezdy")
            }
        }

        // MARK: Trasa

        func syncRoute(_ map: MKMapView, selectedId: String?, route: ODVehicleRoute?) {
            let key: String?
            if let selectedId, let route, route.available {
                key = "\(selectedId)|\(route.traveled.count)|\(route.remaining.count)"
            } else {
                key = nil
            }
            guard key != routeKey else { return }
            routeKey = key
            let old = map.overlays.compactMap { $0 as? ODRoutePolyline }
            if !old.isEmpty { map.removeOverlays(old) }
            guard key != nil, let route else { return }
            if route.traveled.count >= 2 {
                var pts = route.traveled
                let line = ODRoutePolyline(coordinates: &pts, count: pts.count)
                line.isTraveled = true
                map.addOverlay(line, level: .aboveLabels)
            }
            if route.remaining.count >= 2 {
                var pts = route.remaining
                let line = ODRoutePolyline(coordinates: &pts, count: pts.count)
                line.isTraveled = false
                map.addOverlay(line, level: .aboveLabels)
            }
        }

        // MARK: Plynulý pohyb mezi aktualizacemi (jen vizuálně, z `motion`)

        func startTimer(_ map: MKMapView) {
            stopTimer()
            let t = Timer(timeInterval: 1, repeats: true) { [weak self, weak map] _ in
                guard let self, let map else { return }
                self.tick(map)
            }
            RunLoop.main.add(t, forMode: .common)
            timer = t
        }

        func stopTimer() {
            timer?.invalidate()
            timer = nil
        }

        private func tick(_ map: MKMapView) {
            guard map.window != nil, !UIAccessibility.isReduceMotionEnabled else { return }
            let now = Date()
            var moves: [(ODVehicleAnnotation, CLLocationCoordinate2D)] = []
            for ann in vehicleAnns.values {
                let v = ann.vehicle
                guard let vx = v.vx, let vy = v.vy, vx != 0 || vy != 0 else { continue }
                let elapsed = min(max(now.timeIntervalSince(ann.baseTime), 0), 40)
                moves.append((ann, CLLocationCoordinate2D(latitude: v.lat + vy * elapsed, longitude: v.lng + vx * elapsed)))
            }
            guard !moves.isEmpty else { return }
            UIView.animate(withDuration: 1, delay: 0, options: [.curveLinear, .allowUserInteraction]) {
                for (ann, coord) in moves { ann.coordinate = coord }
            }
        }

        // MARK: Klepnutí na prázdnou mapu

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard gesture.state == .ended, let map = gesture.view as? MKMapView else { return }
            var view: UIView? = map.hitTest(gesture.location(in: map), with: nil)
            while let v = view {
                if v is MKAnnotationView { return }
                view = v.superview
            }
            parent.onTapEmpty()
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }

        // MARK: MKMapViewDelegate

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tiles = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tiles)
            }
            if let line = overlay as? ODRoutePolyline {
                let r = MKPolylineRenderer(polyline: line)
                r.lineWidth = 5
                r.lineCap = .round
                r.lineJoin = .round
                if line.isTraveled {
                    r.strokeColor = UIColor(odHex: 0x7a8494, alpha: 0.85)
                    r.lineDashPattern = [1, 8]
                } else {
                    r.strokeColor = UIColor(odHex: parent.routeColor, alpha: 0.95)
                }
                return r
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }

            if let cluster = annotation as? MKClusterAnnotation {
                let id = "od-cluster"
                let view = (mapView.dequeueReusableAnnotationView(withIdentifier: id) as? MKMarkerAnnotationView)
                    ?? MKMarkerAnnotationView(annotation: cluster, reuseIdentifier: id)
                view.annotation = cluster
                view.glyphText = String(cluster.memberAnnotations.count)
                view.markerTintColor = UIColor(odHex: parent.signColor)
                view.titleVisibility = .hidden
                view.subtitleVisibility = .hidden
                view.displayPriority = .defaultLow
                view.accessibilityLabel = L("\(cluster.memberAnnotations.count) zastávek")
                view.accessibilityHint = L("Přiblíží mapu")
                return view
            }

            if let ann = annotation as? ODVehicleAnnotation {
                let view = mapView.dequeueReusableAnnotationView(withIdentifier: "od-veh", for: ann) as? ODVehicleView
                configureView(view, ann: ann, selectedId: parent.selectedVehicleId)
                return view
            }

            if let ann = annotation as? ODStopAnnotation {
                guard let view = mapView.dequeueReusableAnnotationView(withIdentifier: "od-stop", for: ann) as? ODStopView else { return nil }
                configureStopView(view, map: mapView)
                return view
            }
            return nil
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            if let cluster = view.annotation as? MKClusterAnnotation {
                var region = mapView.region
                region.span.latitudeDelta /= 3
                region.span.longitudeDelta /= 3
                region.center = cluster.coordinate
                mapView.setRegion(region, animated: true)
                mapView.deselectAnnotation(cluster, animated: false)
                return
            }
            if let ann = view.annotation as? ODVehicleAnnotation {
                parent.onSelectVehicle(ann.vehicle)
                mapView.deselectAnnotation(ann, animated: false)
            } else if let ann = view.annotation as? ODStopAnnotation {
                parent.onSelectStop(ann.stop)
                mapView.deselectAnnotation(ann, animated: false)
            }
        }

        func mapViewDidChangeVisibleRegion(_ mapView: MKMapView) {}

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            parent.onRegion(mapView.region)
        }
    }
}
