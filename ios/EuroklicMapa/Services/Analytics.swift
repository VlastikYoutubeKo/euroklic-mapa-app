import Foundation

/// The only events the server accepts (whitelist on `api_app_event.php`).
enum AnalyticsEvent: String {
    case appOpen = "app_open"
    case placeOpen = "place_open"
    case navigate
    case search
    case vote
    case placeAdd = "place_add"
    case commentAdd = "comment_add"
    case report
    case modeDeparture = "mode_departures"
}

/// Voluntary, anonymous usage counters. **Off by default**: nothing is sent unless the user turns on
/// "Pomoci vylepšit aplikaci anonymním měřením" in Více. The request carries only
/// `{"event","platform","version"}` — no location, search text, account or device id — and uses its
/// own ephemeral session (no cookies, no auth). Errors are ignored (fire and forget). See PRIVACY.md.
enum Analytics {
    static let enabledKey = "analytics_enabled"

    private static let url = URL(string: "https://euroklic.odjezdy.online/api_app_event.php")!
    private static let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.urlCache = nil
        config.timeoutIntervalForRequest = 10
        return URLSession(configuration: config)
    }()

    static var isEnabled: Bool { UserDefaults.standard.bool(forKey: enabledKey) }

    static func track(_ event: AnalyticsEvent) {
        guard isEnabled else { return }
        // Full name incl. a beta suffix ("1.4.0-beta.1"); the server accepts x.y.z[-beta.N].
        let version = Changelog.currentVersion
        let payload = ["event": event.rawValue, "platform": "ios", "version": version]
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        Task.detached(priority: .background) {
            _ = try? await session.data(for: request)
        }
    }
}
