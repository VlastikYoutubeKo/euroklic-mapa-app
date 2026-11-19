import CoreLocation
import SwiftUI
import UIKit
import WidgetKit

// Sdílené pomůcky extension (záměrně duplikují malou logiku z appky — widget je samostatný
// cíl bez App Groups, nic se nesdílí se zdrojáky appky).

// MARK: - Barvy

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(UIColor(hex: hex))
    }

    /// Barva přepínající se podle světlého / tmavého režimu.
    static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }
}

enum WidgetColors {
    /// Brandová modrá Euroklíče (výplně, odznaky).
    static let euroklic = Color(hex: 0x2454E0)
    /// Modrá pro text a ikony (v tmavém režimu zesvětlená kvůli kontrastu).
    static let euroklicText = Color.adaptive(light: 0x2454E0, dark: 0x8FAEFF)
    /// Zelená Odjezdů (výplně, odznaky).
    static let odjezdy = Color(hex: 0x0B7A4B)
    static let odjezdyText = Color.adaptive(light: 0x0B7A4B, dark: 0x4CC38A)
    /// Zpoždění / varování.
    static let warning = Color.adaptive(light: 0xC4161C, dark: 0xFF8A8A)
    static let background = Color(UIColor.systemBackground)
}

/// Pozadí widgetu; na zamykací obrazovce (accessory) ho řídí systém.
@ViewBuilder
func widgetBackground(for family: WidgetFamily) -> some View {
    switch family {
    case .accessoryCircular, .accessoryRectangular, .accessoryInline:
        Color.clear
    default:
        WidgetColors.background
    }
}

// MARK: - Formátování (port util/GeoUtils)

/// "120 m" / "1,4 km"
func widgetDistance(_ meters: Double) -> String {
    let km = meters / 1000
    if meters < 1000 { return "\(Int(meters.rounded())) m" }
    if km < 100 { return String(format: "%.1f km", km).replacingOccurrences(of: ".", with: appIsCzech ? "," : ".") }
    return "\(Int(km.rounded())) km"
}

/// Hrubý čas pěšky při 4,5 km/h — "~7 min".
func widgetWalkingTime(_ meters: Double) -> String {
    let minutes = max(1, Int((meters / 1000 / 4.5 * 60).rounded()))
    if minutes < 90 { return "~\(minutes) min" }
    if minutes < 24 * 60 { return "~\(Int((Double(minutes) / 60).rounded())) h" }
    return L("přes 1 den")
}

/// "HH:mm" v Europe/Prague bez ohledu na pásmo zařízení.
func pragueClock(_ date: Date) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "cs_CZ")
    f.timeZone = TimeZone(identifier: "Europe/Prague")
    f.dateFormat = "HH:mm"
    return f.string(from: date)
}

func straightLineMeters(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    CLLocation(latitude: lat1, longitude: lon1).distance(from: CLLocation(latitude: lat2, longitude: lon2))
}

// MARK: - Cache (vlastní UserDefaults extension; žádné sdílení s appkou)

enum WidgetCache {
    static func save<T: Encodable>(_ value: T, key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}

// MARK: - HTTP

enum WidgetHTTPError: Error {
    case badStatus(Int)
    case badBody
}

enum WidgetHTTP {
    /// GET s krátkým timeoutem, efemérní session (žádné cookies), kontrola HTTP stavu.
    static func get(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: 10)
        request.setValue("EuroklicMapa-iOS-Widget/1.0 (euroklic.odjezdy.online)", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession(configuration: .ephemeral).data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw WidgetHTTPError.badStatus(http.statusCode)
        }
        return data
    }
}

// MARK: - Poloha (widget si ji zjišťuje sám; vyžaduje NSWidgetWantsLocation)

@MainActor
final class WidgetLocationProvider: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation?, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var isAuthorized: Bool {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse: return true
        default: return false
        }
    }

    /// `nil` = bez oprávnění nebo bez zaměření. Nikdy nevisí déle než ~8 s.
    func currentLocation() async -> CLLocation? {
        guard isAuthorized else { return nil }
        if let l = manager.location, l.horizontalAccuracy >= 0, abs(l.timestamp.timeIntervalSinceNow) < 600 {
            return l
        }
        return await withCheckedContinuation { (cont: CheckedContinuation<CLLocation?, Never>) in
            continuation = cont
            manager.requestLocation()
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                self?.finish(self?.manager.location)
            }
        }
    }

    private func finish(_ location: CLLocation?) {
        guard let c = continuation else { return }
        continuation = nil
        c.resume(returning: location)
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let last = locations.last
        Task { @MainActor in self.finish(last) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.finish(self.manager.location) }
    }
}

/// Nejčastější obnova timeline (WidgetKit stejně dovolí jen omezený počet obnov za hodinu).
let widgetRefreshInterval: TimeInterval = 15 * 60
