import AuthenticationServices
import CryptoKit
import Foundation
import Security
import UIKit

// MARK: - Keychain

/// Bearer token lives in the Keychain (device-protected), never UserDefaults.
struct KeychainStore {
    static let shared = KeychainStore()
    private let service = "cz.euroklicmapa.ios.token"

    var token: String? {
        var query = base
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func save(_ token: String) {
        delete()
        var query = base
        query[kSecValueData as String] = Data(token.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(query as CFDictionary, nil)
    }

    func delete() {
        SecItemDelete(base as CFDictionary)
    }

    private var base: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "bearer",
        ]
    }
}

// MARK: - PKCE login (RFC 8252) — same flow as the Android app

/// 1. `state` nonce + `code_verifier` (kept local), 2. hosted login page in the
/// system web sheet, 3. callback `euroklicmapa://auth-callback?code=…&state=…`
/// (legacy `?token=` still accepted until the backend removes that branch),
/// 4. exchange `code` + verifier at `/api_token.php` → Bearer → Keychain.
@MainActor
final class AuthSession: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    @Published private(set) var me: MeResponse?
    @Published private(set) var loading = false

    private let api: EuroklicAPI
    private var webSession: ASWebAuthenticationSession?

    init(api: EuroklicAPI = EuroklicAPI()) {
        self.api = api
        super.init()
        Task { await restoreMe() }
    }

    var isLoggedIn: Bool { me?.logged_in == true }
    var username: String? { me?.username }
    var isAdmin: Bool { me?.is_admin == true }

    /// Restore session from the stored token (decrypt-on-launch, like Android).
    func restoreMe() async {
        guard KeychainStore.shared.token != nil else { return }
        loading = true
        defer { loading = false }
        me = try? await api.me()
    }

    func login() {
        let state = Self.randomURLSafe(byteCount: 16)
        let verifier = Self.randomURLSafe(byteCount: 32)
        guard let challenge = Self.s256Base64URL(verifier) else { return }

        var components = URLComponents(url: EuroklicAPI.baseURL.appendingPathComponent("app-login.php"), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "client", value: "app"),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]

        let session = ASWebAuthenticationSession(url: components.url!, callbackURLScheme: "euroklicmapa") { [weak self] callback, error in
            Task { @MainActor in
                self?.handleCallback(callback, error: error, state: state, verifier: verifier)
            }
        }
        session.presentationContextProvider = self
        // A fresh session avoids reusing a logged-in web session silently — same as Android.
        session.prefersEphemeralWebBrowserSession = false
        webSession = session
        session.start()
    }

    private func handleCallback(_ callback: URL?, error: Error?, state expectedState: String, verifier: String) {
        guard error == nil, let callback else { return }
        let params = URLComponents(url: callback, resolvingAgainstBaseURL: false)?.queryItems ?? []

        // Legacy `?token=` callback still works until the backend cuts that branch.
        if let legacy = params.first(where: { $0.name == "token" })?.value, !legacy.isEmpty {
            finishWithToken(legacy)
            return
        }

        // The state check is real security: a shell-injected deep link with a
        // mismatched/missing nonce is rejected (verified on Android too).
        guard params.first(where: { $0.name == "state" })?.value == expectedState,
              let code = params.first(where: { $0.name == "code" })?.value, !code.isEmpty
        else { return }

        loading = true
        Task { @MainActor in
            defer { loading = false }
            guard let response = try? await api.exchangeToken(code: code, codeVerifier: verifier),
                  let token = response.token, !token.isEmpty
            else { return }
            finishWithToken(token)
        }
    }

    private func finishWithToken(_ token: String) {
        KeychainStore.shared.save(token)
        loading = true
        Task { @MainActor in
            defer { loading = false }
            me = try? await api.me()
        }
    }

    func logout() async {
        _ = try? await api.logout() // revokes this token server-side
        KeychainStore.shared.delete()
        me = nil
    }

    // MARK: - Presentation anchor

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }

    // MARK: - PKCE helpers

    static func randomURLSafe(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        _ = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        return Data(bytes).base64URLEncodedString()
    }

    static func s256Base64URL(_ verifier: String) -> String? {
        Data(SHA256.hash(data: Data(verifier.utf8))).base64URLEncodedString()
    }
}

extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
