import Foundation

enum APIError: LocalizedError {
    /// Backend answered 200 with `{"success":false,...}` or an `error` string.
    case serverMessage(String)
    /// Status code wasn't 200.
    case http(Int)
    /// Body wasn't the expected JSON (missing path serves homepage HTML with 200!).
    case decoding
    case network(Error)

    var errorDescription: String? {
        switch self {
        case .serverMessage(let msg): return msg
        case .http(let code): return "Server vrátil chybu (\(code))."
        case .decoding: return "Server odpověděl neočekávaně. Zkuste to znovu."
        case .network: return "Nepodařilo se spojit se serverem. Zkontrolujte připojení."
        }
    }
}

/// Thin async/await client over `URLSession.shared` (cookies persist in
/// `HTTPCookieStorage.shared` — required for the CSRF/vote flow — and the
/// default `URLCache` transparently handles the feeds' ETag/304 revalidation).
struct EuroklicAPI {
    static let baseURL = URL(string: "https://euroklic.odjezdy.online/")!

    /// Bearer token injected into every request when present (Keychain-backed).
    var tokenProvider: () -> String? = { KeychainStore.shared.token }

    // MARK: - Feeds

    /// `near` = `"lat,lon"`, radius 1–500. Omit both for the full feed.
    func locations(near: String? = nil, radiusKm: Int? = nil) async throws -> FeatureCollection<WcProperties> {
        try await get("api_locations.php", query: nearQuery(near: near, radiusKm: radiusKm))
    }

    func pickupPoints(near: String? = nil, radiusKm: Int? = nil) async throws -> FeatureCollection<PickupProperties> {
        try await get("api_pickup_points.php", query: nearQuery(near: near, radiusKm: radiusKm))
    }

    private func nearQuery(near: String?, radiusKm: Int?) -> [URLQueryItem] {
        var q: [URLQueryItem] = []
        if let near { q.append(URLQueryItem(name: "near", value: near)) }
        if let radiusKm { q.append(URLQueryItem(name: "radius_km", value: String(radiusKm))) }
        return q
    }

    // MARK: - Voting (anonymous, cookie + CSRF)

    func csrf() async throws -> CsrfResponse {
        try await get("api_csrf.php")
    }

    /// `type` is `"like"` or `"dislike"`. Deduped by IP server-side.
    func vote(id: Int, type: String, csrfToken: String) async throws -> VoteResponse {
        var request = formRequest("api_vote.php", fields: ["id": String(id), "type": type])
        request.setValue(csrfToken, forHTTPHeaderField: "X-CSRF-Token")
        return try await run(request)
    }

    // MARK: - Comments (read-only; writing happens on the web)

    func comments(locationId: Int) async throws -> CommentsResponse {
        try await get("api_comments.php", query: [URLQueryItem(name: "location_id", value: String(locationId))])
    }

    // MARK: - Auth (PKCE; Bearer added below when present)

    func exchangeToken(code: String, codeVerifier: String) async throws -> TokenResponse {
        let request = formRequest("api_token.php", fields: ["code": code, "code_verifier": codeVerifier])
        return try await run(request)
    }

    func me() async throws -> MeResponse {
        try await get("api_me.php")
    }

    func logout() async throws -> ApiResult {
        try await run(formRequest("api_logout.php", fields: [:], method: "POST"))
    }

    // MARK: - Add place (Bearer; no CSRF with a token)

    func addPlace(name: String, desc: String, lat: Double, lon: Double, photoJPEG: Data?) async throws -> ApiResult {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: Self.baseURL.appendingPathComponent("api_add.php"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token = tokenProvider() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        var body = Data()
        func addField(_ name: String, _ value: String) {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }
        addField("name", name)
        addField("desc", desc)
        addField("lat", String(lat))
        addField("lon", String(lon))
        if let photoJPEG, !photoJPEG.isEmpty {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"photo_file\"; filename=\"photo.jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(photoJPEG)
            body.append("\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        return try await run(request)
    }

    // MARK: - Plumbing

    private func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        var components = URLComponents(url: Self.baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        var request = URLRequest(url: components.url!)
        authorize(&request)
        return try await run(request)
    }

    private func formRequest(_ path: String, fields: [String: String], method: String = "POST") -> URLRequest {
        var request = URLRequest(url: Self.baseURL.appendingPathComponent(path))
        request.httpMethod = method
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let body = fields.map { "\($0.key)=\(urlEncode($0.value))" }.joined(separator: "&")
        request.httpBody = body.data(using: .utf8)
        authorize(&request)
        return request
    }

    private func urlEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func authorize(_ request: inout URLRequest) {
        if let token = tokenProvider() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
    }

    private func run<T: Decodable>(_ request: URLRequest) async throws -> T {
        let data: Data, response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw APIError.network(error)
        }
        guard let http = response as? HTTPURLResponse else { throw APIError.decoding }
        guard http.statusCode == 200 else { throw APIError.http(http.statusCode) }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            // Missing path → the homepage HTML with 200. Surface as friendly error.
            throw APIError.decoding
        }
    }
}
