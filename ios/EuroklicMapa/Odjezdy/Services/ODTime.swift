import Foundation

/// Tolerantní práce s časy z odjezdy.online (UTC "Z", "+01:00", bez zóny, holé "HH:MM[:SS]").
/// Zobrazuje se vždy v Europe/Prague.
enum ODTime {
    static let prague: TimeZone = TimeZone(identifier: "Europe/Prague") ?? TimeZone.current

    private static let isoFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let isoPlain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private static func localFormatter(_ format: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = prague
        f.dateFormat = format
        return f
    }

    private static let localSeconds = localFormatter("yyyy-MM-dd'T'HH:mm:ss")
    private static let localMinutes = localFormatter("yyyy-MM-dd'T'HH:mm")
    private static let hmFormatter = localFormatter("HH:mm")

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "cs_CZ")
        f.timeZone = prague
        f.dateFormat = "d. M. HH:mm"
        return f
    }()

    private static var pragueCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = prague
        return cal
    }

    /// Převede text na datum. Holé "HH:MM" se vztahuje k dnešku v Praze
    /// (a posune o den, pokud by vyšlo víc než 12 h od `now`).
    static func parse(_ raw: String?, now: Date = Date()) -> Date? {
        guard var s = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty else { return nil }

        // Holé "HH:MM[:SS]".
        if s.count <= 8, s.contains(":"), !s.contains("-") {
            let parts = s.split(separator: ":").map { Int($0) }
            guard parts.count >= 2, let h = parts[0], let m = parts[1],
                  (0...24).contains(h), (0...59).contains(m) else { return nil }
            let sec: Int = parts.count > 2 ? (parts[2] ?? 0) : 0
            let cal = pragueCalendar
            var comps = cal.dateComponents([.year, .month, .day], from: now)
            comps.hour = h
            comps.minute = m
            comps.second = sec
            guard var date = cal.date(from: comps) else { return nil }
            if date.timeIntervalSince(now) < -12 * 3600 {
                date = date.addingTimeInterval(24 * 3600)
            } else if date.timeIntervalSince(now) > 12 * 3600 {
                date = date.addingTimeInterval(-24 * 3600)
            }
            return date
        }

        // "2026-11-19 07:32:00+01:00" (mezera místo T).
        if s.count > 10 {
            let idx = s.index(s.startIndex, offsetBy: 10)
            if s[idx] == " " { s.replaceSubrange(idx...idx, with: "T") }
        }
        if let d = isoFraction.date(from: s) { return d }
        if let d = isoPlain.date(from: s) { return d }

        // Zlomky sekund v jiné délce než 3 číslice: odstranit a zkusit znovu.
        if let dot = s.firstIndex(of: ".") {
            var end = s.index(after: dot)
            while end < s.endIndex, s[end].isNumber { end = s.index(after: end) }
            var stripped = s
            stripped.removeSubrange(dot..<end)
            if let d = isoPlain.date(from: stripped) { return d }
            s = stripped
        }

        // Bez zóny = místní čas.
        if let d = localSeconds.date(from: s) { return d }
        if let d = localMinutes.date(from: s) { return d }
        return nil
    }

    /// "07:32" v Praze.
    static func hm(_ date: Date) -> String { hmFormatter.string(from: date) }

    /// "19. 11. 07:32" v Praze.
    static func dayAndTime(_ date: Date) -> String { dayFormatter.string(from: date) }

    /// Celé minuty do `date` (zaokrouhleno dolů, záporné = už odjelo).
    static func minutesUntil(_ date: Date, now: Date = Date()) -> Int {
        Int((date.timeIntervalSince(now) / 60).rounded(.down))
    }
}
