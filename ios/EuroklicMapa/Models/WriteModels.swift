import Foundation

/// Důvody pro `POST /api_report.php` — přesné hodnoty na drátě (shodné s Androidem).
enum ReportReason: String, CaseIterable, Identifiable {
    case notFound = "not_found"
    case keyBroken = "key_broken"
    case wrongLocation = "wrong_location"
    case wrongInfo = "wrong_info"
    case badPhoto = "bad_photo"
    case other = "other"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .notFound: return L("Místo už neexistuje")
        case .keyBroken: return L("Euroklíč nefunguje")
        case .wrongLocation: return L("Špatná poloha")
        case .wrongInfo: return L("Špatné informace")
        case .badPhoto: return L("Nevhodná fotografie")
        case .other: return L("Jiné")
        }
    }

    /// Lidsky čitelný název pro admin frontu; neznámé hodnoty vrací beze změny.
    static func label(forWire raw: String) -> String {
        ReportReason(rawValue: raw)?.label ?? raw
    }
}

/// Tolerantní tvar odpovědi zápisových endpointů (`{status, success, error?, message}`);
/// chybové HTTP kódy mohou vracet jen část polí.
struct WriteReply: Decodable {
    let success: Bool?
    let error: String?
    let message: String?

    var text: String? {
        [error, message].compactMap { $0 }.first { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
    }
}

/// Výsledek zápisu už přeložený do češtiny pro uživatele.
enum WriteResult {
    case ok(String)
    case failed(String)
    /// Jen admin: položku už vyřídil někdo jiný (404) — řádek se má odebrat.
    case gone(String)

    var message: String {
        switch self {
        case .ok(let m), .failed(let m), .gone(let m): return m
        }
    }
    var isOk: Bool { if case .ok = self { return true } else { return false } }
}

// MARK: - Admin fronta

/// `GET /api_admin_list.php`. Chybějící klíče = prázdné pole.
struct AdminListResponse: Decodable {
    let error: String?
    let locations: [AdminPlace]?
    let photo_suggestions: [PhotoSuggestion]?
    let comment_suggestions: [CommentSuggestion]?
    let location_reports: [LocationReport]?
}

struct AdminPlace: Decodable, Identifiable {
    let id: Int
    let name: String?
    let description: String?
    let lat: Double?
    let lon: Double?
    let photo_url: String?
    let author_name: String?
    let created_at: String?
}

struct PhotoSuggestion: Decodable, Identifiable {
    let id: Int
    let location_id: Int?
    let photo_url: String?
    let author_name: String?
    let location_name: String?
}

struct CommentSuggestion: Decodable, Identifiable {
    let id: Int
    let location_id: Int?
    let text: String?
    let author_name: String?
    let author: String?
    let location_name: String?

    var displayAuthor: String? {
        [author_name, author].compactMap { $0 }.first { !$0.isEmpty }
    }
}

struct LocationReport: Decodable, Identifiable {
    let id: Int
    let location_id: Int
    let location_name: String?
    let reason: String?
    let note: String?
    let author_name: String?
    let created_at: String?
}
