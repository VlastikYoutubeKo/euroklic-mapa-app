import Foundation

/// One community note on a place — verified live 2026-09-05:
/// `{"status":"success","comments":[{"text":...,"author":"Anonym","created_at":"YYYY-MM-DD HH:MM:SS"}]}`.
struct WcComment: Decodable, Identifiable {
    let text: String
    let author: String?
    let created_at: String?

    var id: String { (author ?? "") + "|" + text }
    var authorName: String { (author?.isEmpty == false) ? author! : "Anonym" }

    /// `"2026-08-28 04:31:45"` → `"28. 8. 2026"` — the same date-only display as the web.
    var dayLabel: String? {
        guard let created_at, !created_at.isEmpty else { return nil }
        let parts = created_at.split(separator: " ").first.map(String.init) ?? created_at
        let ymd = parts.split(separator: "-")
        guard ymd.count == 3,
              let d = Int(ymd[2]), let m = Int(ymd[1]), let y = Int(ymd[0])
        else { return parts }
        return "\(d). \(m). \(y)"
    }
}

struct CommentsResponse: Decodable {
    let status: String?
    let message: String?
    let comments: [WcComment]?
}
