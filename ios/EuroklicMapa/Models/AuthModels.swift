import Foundation

/// `POST /api_token.php` — PKCE code → Bearer token exchange. HTTP 200 even on failure.
struct TokenResponse: Decodable {
    let token: String?
    let error: String?
}

/// `GET /api_me.php` — works unauthenticated too (`logged_in: false`).
struct MeResponse: Decodable {
    let logged_in: Bool
    let user_id: String?
    let username: String?
    let is_admin: Bool
    let login_provider: String?
    let avatar_url: String?
}

/// Shape shared by `/api_logout.php`, `/api_add.php` (HTTP 200 even on failure — check `success`).
struct ApiResult: Decodable {
    let success: Bool
    let error: String?
    let message: String?
    let id: Int?
}

struct CsrfResponse: Decodable {
    let csrf: String?
}

struct VoteResponse: Decodable {
    let success: Bool
    let message: String?
    let error: String?
    let likes: Int?
    let dislikes: Int?
}
