import Foundation

/// Which way this device voted on a place. `/api_vote.php` is anonymous and dedups by IP on the
/// server, so without this the buttons forget the vote after a restart and invite a repeat that the
/// server then rejects. `true` = "funguje", `false` = "nefunguje", missing = not voted (that we know of).
enum VoteMemory {
    private static let key = "place_votes"

    static func vote(for placeId: Int) -> Bool? {
        let all = UserDefaults.standard.dictionary(forKey: key) as? [String: Bool]
        return all?[String(placeId)]
    }

    static func save(_ like: Bool, for placeId: Int) {
        var all = (UserDefaults.standard.dictionary(forKey: key) as? [String: Bool]) ?? [:]
        all[String(placeId)] = like
        UserDefaults.standard.set(all, forKey: key)
    }
}
