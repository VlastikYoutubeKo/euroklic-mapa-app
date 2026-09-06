import CoreLocation
import Foundation

/// Single shared source of the user's location. App stays fully functional
/// when the permission is denied — callers degrade to alphabetical order and
/// hide distance labels (same contract as Android `LocationRepository`).
@MainActor
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var lastLocation: CLLocation?
    @Published var authorizationDenied = false

    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestAndRefresh() {
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            authorizationDenied = true
        default:
            manager.requestLocation()
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            switch manager.authorizationStatus {
            case .authorizedWhenInUse, .authorizedAlways:
                authorizationDenied = false
                manager.requestLocation()
            case .denied, .restricted:
                authorizationDenied = true
            default:
                break
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            lastLocation = locations.last
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            // Keep whatever lastKnown we had; UI just won't re-sort.
            if lastLocation == nil { authorizationDenied = true }
        }
    }

    var nearParameter: String? {
        guard let lastLocation else { return nil }
        return String(format: "%.5f,%.5f", lastLocation.coordinate.latitude, lastLocation.coordinate.longitude)
    }
}
