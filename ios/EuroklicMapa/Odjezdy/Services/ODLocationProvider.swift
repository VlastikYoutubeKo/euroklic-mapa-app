import CoreLocation
import Foundation

/// Malý vlastní zdroj polohy pro režim Odjezdy (nezávislý na zbytku appky).
/// Oprávnění se žádá až na přání uživatele (tlačítko "Moje poloha"); appka funguje i bez něj.
final class ODLocationProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published private(set) var location: CLLocation?
    @Published private(set) var status: CLAuthorizationStatus

    private let manager: CLLocationManager

    override init() {
        let m = CLLocationManager()
        manager = m
        status = m.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 50
        startIfAllowed()
    }

    var isAuthorized: Bool {
        status == .authorizedWhenInUse || status == .authorizedAlways
    }

    var isDenied: Bool {
        status == .denied || status == .restricted
    }

    /// Zeptá se na oprávnění (první klepnutí) nebo jen obnoví polohu.
    func request() {
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if isAuthorized {
            manager.requestLocation()
        }
    }

    private func startIfAllowed() {
        guard isAuthorized else { return }
        manager.startUpdatingLocation()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        status = manager.authorizationStatus
        if isAuthorized {
            manager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let last = locations.last, last.horizontalAccuracy >= 0 else { return }
        location = last
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Bez polohy appka běží dál; nic neukazujeme.
    }
}
