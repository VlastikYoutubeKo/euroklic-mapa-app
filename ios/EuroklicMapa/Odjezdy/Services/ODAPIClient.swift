import Foundation

/// Chyby klienta odjezdy.online – česky, bez technických detailů.
enum ODAPIError: LocalizedError {
    case offline
    case rateLimited(until: Date)
    case server(Int)
    case api(String)
    case badData

    var errorDescription: String? {
        switch self {
        case .offline:
            return L("Jste offline. Zkontrolujte připojení k internetu.")
        case .rateLimited:
            return L("Server odjezdy.online je dočasně přetížený. Zkusíme to za chvilku.")
        case .server(let code):
            return L("Server odjezdy.online neodpovídá správně (kód \(code)).")
        case .api(let message):
            return message.isEmpty ? L("Server vrátil chybu.") : message
        case .badData:
            return L("Odpověď serveru se nepodařilo přečíst.")
        }
    }

    var isOffline: Bool {
        if case .offline = self { return true }
        return false
    }
}

private struct ODErrorEnvelope: Decodable {
    let errorText: String?

    enum CodingKeys: String, CodingKey { case error, message, success }
    struct Inner: Decodable { let message: String? }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .error) {
            errorText = s
        } else if let inner = try? c.decode(Inner.self, forKey: .error) {
            errorText = inner.message ?? L("Chyba")
        } else if let ok = try? c.decode(Bool.self, forKey: .success), ok == false {
            errorText = (try? c.decode(String.self, forKey: .message)) ?? L("Chyba")
        } else {
            errorText = nil
        }
    }
}

private struct ODDataEnvelope<T: Decodable>: Decodable {
    let data: [ODLossy<T>]?
}

/// Klient veřejného API app.odjezdy.online (jen čtení, bez klíče, bez cookies).
///
/// Dodržuje `docs/odjezdy-api-for-ios.md`: jedno volání v letu na stejnou URL, krátká cache
/// (aby se zbytečně neplýtvalo limitem 150/min), při 429 čeká do `X-RateLimit-Reset`.
actor ODAPIClient {
    static let shared = ODAPIClient()

    private let session: URLSession
    /// Plánovač spojení: první dotaz dne může trvat i minutu (server staví index), proto delší limity.
    private let longSession: URLSession
    private let base = "https://app.odjezdy.online"
    private var blockedUntil: Date?
    private var cache: [String: (stored: Date, data: Data)] = [:]
    private var inflight: [String: Task<Data, Error>] = [:]

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 45
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil
        session = URLSession(configuration: config)
        let longConfig = URLSessionConfiguration.ephemeral
        longConfig.requestCachePolicy = .reloadIgnoringLocalCacheData
        longConfig.timeoutIntervalForRequest = 100
        longConfig.timeoutIntervalForResource = 110
        longConfig.httpCookieStorage = nil
        longConfig.httpShouldSetCookies = false
        longConfig.urlCredentialStorage = nil
        longSession = URLSession(configuration: longConfig)
    }

    // MARK: Jádro

    private static let segmentAllowed: CharacterSet = {
        var set = CharacterSet.urlPathAllowed
        set.remove(charactersIn: "/")
        return set
    }()

    private func makeURL(path: String, segment: String? = nil, query: [(String, String)] = []) -> URL? {
        var comps = URLComponents(string: base)
        if let segment {
            let enc = segment.addingPercentEncoding(withAllowedCharacters: Self.segmentAllowed) ?? segment
            comps?.percentEncodedPath = path + enc
        } else {
            comps?.path = path
        }
        if !query.isEmpty {
            comps?.queryItems = query.map { URLQueryItem(name: $0.0, value: $0.1) }
        }
        return comps?.url
    }

    /// Je zablokováno kvůli 429? (pro UI: kolik sekund počkat)
    func retryAfter() -> TimeInterval? {
        guard let until = blockedUntil else { return nil }
        let left = until.timeIntervalSinceNow
        return left > 0 ? left : nil
    }

    private func load(_ url: URL, ttl: TimeInterval, timeout: TimeInterval = 20, long: Bool = false) async throws -> Data {
        let key = url.absoluteString
        let now = Date()
        if ttl > 0, let hit = cache[key], now.timeIntervalSince(hit.stored) < ttl {
            return hit.data
        }
        if let until = blockedUntil, until > now {
            // Při limitu raději ukázat starší data, než volat dál.
            if let hit = cache[key] { return hit.data }
            throw ODAPIError.rateLimited(until: until)
        }
        if let running = inflight[key] {
            return try await running.value
        }
        let session = long ? self.longSession : self.session
        let task = Task<Data, Error> { try await Self.perform(session: session, url: url, timeout: timeout) }
        inflight[key] = task
        do {
            let data = try await task.value
            inflight[key] = nil
            store(key, data)
            return data
        } catch let error as ODAPIError {
            inflight[key] = nil
            if case .rateLimited(let until) = error { blockedUntil = until }
            throw error
        } catch {
            inflight[key] = nil
            throw error
        }
    }

    private func store(_ key: String, _ data: Data) {
        cache[key] = (Date(), data)
        if cache.count > 60 {
            let sorted = cache.sorted { $0.value.stored < $1.value.stored }
            for (k, _) in sorted.prefix(cache.count - 40) { cache[k] = nil }
        }
    }

    private static func perform(session: URLSession, url: URL, timeout: TimeInterval) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: timeout)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("gzip", forHTTPHeaderField: "Accept-Encoding")
        request.setValue("EuroklicMapa-iOS/1.0 (odjezdy)", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw ODAPIError.badData }
            if http.statusCode == 429 {
                var until = Date().addingTimeInterval(30)
                if let reset = http.value(forHTTPHeaderField: "X-RateLimit-Reset"), let epoch = Double(reset) {
                    let target = Date(timeIntervalSince1970: epoch)
                    // Strop 2 min, aby špatná hlavička appku nezablokovala napořád.
                    until = min(max(target, Date().addingTimeInterval(2)), Date().addingTimeInterval(120))
                }
                throw ODAPIError.rateLimited(until: until)
            }
            guard (200..<300).contains(http.statusCode) else {
                // 4xx/5xx s tělem {error:{message}} -> hláška serveru.
                if let env = try? JSONDecoder().decode(ODErrorEnvelope.self, from: data), let text = env.errorText {
                    throw ODAPIError.api(text)
                }
                throw ODAPIError.server(http.statusCode)
            }
            return data
        } catch let error as ODAPIError {
            throw error
        } catch let error as URLError {
            switch error.code {
            case .notConnectedToInternet, .networkConnectionLost, .dataNotAllowed, .cannotConnectToHost,
                 .cannotFindHost, .dnsLookupFailed, .timedOut, .internationalRoamingOff:
                throw ODAPIError.offline
            case .cancelled:
                throw CancellationError()
            default:
                throw ODAPIError.offline
            }
        }
    }

    /// 200 s `{"error": ...}` je chyba, ne data.
    private func checkBody(_ data: Data) throws {
        if let env = try? JSONDecoder().decode(ODErrorEnvelope.self, from: data), let text = env.errorText {
            throw ODAPIError.api(text)
        }
    }

    private func decodeList<T: Decodable>(_ type: T.Type, from data: Data) throws -> [T] {
        let decoder = JSONDecoder()
        if let env = try? decoder.decode(ODDataEnvelope<T>.self, from: data), let list = env.data {
            return list.compactMap { $0.value }
        }
        if let bare = try? decoder.decode([ODLossy<T>].self, from: data) {
            return bare.compactMap { $0.value }
        }
        try checkBody(data)
        throw ODAPIError.badData
    }

    // MARK: Vozidla

    /// Všechna vozidla (celá ČR, ~0,7 MB gzip). Volat jen při viditelné mapě, každých 15–20 s.
    func vehicles() async throws -> [ODVehicle] {
        guard let url = makeURL(path: "/api/vehicles", query: [("fields", "map")]) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 8, timeout: 40)
        return try decodeList(ODVehicle.self, from: data)
    }

    func vehicleRoute(id: String) async throws -> ODVehicleRoute {
        guard let url = makeURL(path: "/api/vehicle-route/", segment: id) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 15)
        try checkBody(data)
        guard let route = try? JSONDecoder().decode(ODVehicleRoute.self, from: data) else { throw ODAPIError.badData }
        return route
    }

    func vehicleInfo(id: String, fresh: Bool = false) async throws -> ODVehicleInfo {
        guard let url = makeURL(path: "/api/vehicle-info/", segment: id) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: fresh ? 0 : 120)
        try checkBody(data)
        guard let info = try? JSONDecoder().decode(ODVehicleInfo.self, from: data) else { throw ODAPIError.badData }
        return info
    }

    func averageDelay(provider: String, line: String, destination: String?, tripId: String) async throws -> ODDelayAverage {
        var q: [(String, String)] = [("tripId", tripId)]
        if !provider.isEmpty { q.append(("provider", provider)) }
        if !line.isEmpty { q.append(("line", line)) }
        if let destination, !destination.isEmpty { q.append(("destination", destination)) }
        guard let url = makeURL(path: "/api/delays/average", query: q) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 600)
        try checkBody(data)
        guard let avg = try? JSONDecoder().decode(ODDelayAverage.self, from: data) else { throw ODAPIError.badData }
        return avg
    }

    // MARK: Zastávky

    func stops(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) async throws -> [ODStop] {
        let q: [(String, String)] = [
            ("minLat", String(format: "%.5f", minLat)),
            ("maxLat", String(format: "%.5f", maxLat)),
            ("minLng", String(format: "%.5f", minLng)),
            ("maxLng", String(format: "%.5f", maxLng))
        ]
        guard let url = makeURL(path: "/api/stations/bounds", query: q) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 60)
        return try decodeList(ODStop.self, from: data)
    }

    /// Hledání zastávek (v2, při selhání starý endpoint).
    func searchStops(query: String) async throws -> [ODStop] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return [] }
        if let url = makeURL(path: "/api/v2/stops/search", query: [("q", q)]) {
            do {
                let data = try await load(url, ttl: 300)
                return try decodeList(ODStop.self, from: data)
            } catch let error as ODAPIError {
                if case .rateLimited = error { throw error }
                if error.isOffline { throw error }
            }
        }
        guard let url = makeURL(path: "/api/stations/search", query: [("q", q)]) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 300)
        return try decodeList(ODStop.self, from: data)
    }

    /// Hledání vozidel podle ev. č. / SPZ (`/api/search`, jen část `vehicles`).
    func searchVehicles(query: String) async throws -> [ODVehicle] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty, let url = makeURL(path: "/api/search", query: [("q", q)]) else { return [] }
        let data = try await load(url, ttl: 120)
        struct Wrapper: Decodable {
            let vehicles: [ODLossy<ODVehicle>]?
        }
        let w = try? JSONDecoder().decode(Wrapper.self, from: data)
        return (w?.vehicles ?? []).compactMap { $0.value }
    }

    // MARK: Odjezdy

    /// Odjezdy z celé zastávky (uzel se všemi stanovišti). v2, při selhání starý `/api/board`.
    func departures(stopId: String, limit: Int = 80, minutes: Int = 360) async throws -> [ODDeparture] {
        let q: [(String, String)] = [
            ("stopId", stopId),
            ("limit", String(limit)),
            ("minutes", String(minutes))
        ]
        if let url = makeURL(path: "/api/v2/departures", query: q) {
            do {
                let data = try await load(url, ttl: 20)
                try checkBody(data)
                return try decodeList(ODDeparture.self, from: data)
            } catch let error as ODAPIError {
                if case .rateLimited = error { throw error }
                if error.isOffline { throw error }
            }
        }
        guard let url = makeURL(path: "/api/board/", segment: stopId) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 20)
        try checkBody(data)
        return try decodeList(ODDeparture.self, from: data)
    }

    /// Odjezdy z více zastávek jedním dotazem (`stopIds=a,b,c`, server je sloučí; každý odjezd nese své `stopId`).
    /// `limit` platí pro všechny zastávky dohromady, proto ho volající násobí počtem zastávek.
    func departures(stopIds: [String], limit: Int, minutes: Int) async throws -> [ODDeparture] {
        guard !stopIds.isEmpty else { return [] }
        let q: [(String, String)] = [
            ("stopIds", stopIds.joined(separator: ",")),
            ("limit", String(limit)),
            ("minutes", String(minutes))
        ]
        guard let url = makeURL(path: "/api/v2/departures", query: q) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 20)
        try checkBody(data)
        return try decodeList(ODDeparture.self, from: data)
    }

    /// Zastávky v okolí včetně jejich nejbližších odjezdů jedním dotazem (server slučuje nástupiště pod uzel).
    func nearbyDepartures(lat: Double, lng: Double, radius: Int, maxStops: Int, limit: Int, minutes: Int) async throws -> [ODNearbyStopDepartures] {
        let q: [(String, String)] = [
            ("lat", String(format: "%.5f", lat)),
            ("lng", String(format: "%.5f", lng)),
            ("radius", String(radius)),
            ("maxStops", String(maxStops)),
            ("limit", String(limit)),
            ("minutes", String(minutes))
        ]
        guard let url = makeURL(path: "/api/v2/nearby-departures", query: q) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 20)
        try checkBody(data)
        guard let raw = try? JSONDecoder().decode(ODNearbyRaw.self, from: data), let stops = raw.stops else {
            throw ODAPIError.badData
        }
        return stops.compactMap { $0.value }.map { dto in
            ODNearbyStopDepartures(
                stop: ODStop(id: dto.id, name: dto.name, lat: dto.lat, lng: dto.lng, provider: dto.provider ?? ""),
                distanceM: Int((dto.distanceMeters ?? 0).rounded()),
                departures: (dto.departures ?? []).compactMap { $0.value },
                unavailable: dto.unavailable ?? false
            )
        }
    }

    /// Spojení z polohy `from` k místu `to` (dveře–dveře: chůze k zastávce, jízda, chůze k cíli).
    /// `radius` (100–2000 m) jen když chceme širší okolí zastávek než výchozích 800 m.
    func journey(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, time: String, radius: Int? = nil) async throws -> JourneyResult {
        var q: [(String, String)] = [
            ("fromLat", String(format: "%.5f", fromLat)),
            ("fromLng", String(format: "%.5f", fromLng)),
            ("toLat", String(format: "%.5f", toLat)),
            ("toLng", String(format: "%.5f", toLng)),
            ("time", time),
            ("variants", "3")
        ]
        if let radius { q.append(("radius", String(radius))) }
        guard let url = makeURL(path: "/api/v2/journey", query: q) else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 120, timeout: 100, long: true)
        return try JourneyResult.decode(data)
    }

    // MARK: Aktuality

    func alerts() async throws -> [ODAlert] {
        guard let url = makeURL(path: "/api/alerts") else { throw ODAPIError.badData }
        let data = try await load(url, ttl: 120)
        return try decodeList(ODAlert.self, from: data)
    }
}

/// Zastávka z `/api/v2/nearby-departures` s odjezdy; `unavailable` = odjezdy se teď nepodařilo načíst.
struct ODNearbyStopDepartures {
    let stop: ODStop
    let distanceM: Int
    let departures: [ODDeparture]
    let unavailable: Bool
}

private struct ODNearbyRaw: Decodable {
    let stops: [ODLossy<ODNearbyStopDTO>]?
}

private struct ODNearbyStopDTO: Decodable {
    let id: String
    let name: String
    let lat: Double
    let lng: Double
    let provider: String?
    let distanceMeters: Double?
    let unavailable: Bool?
    let departures: [ODLossy<ODDeparture>]?
}
