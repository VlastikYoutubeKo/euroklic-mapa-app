import XCTest
@testable import EuroklicMapa

/// Regression guard for the website-mirrored trust logic (same cases the
/// Android test should cover — do not let the two drift apart).
final class PlaceStatusTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000) // fixed "today"

    private func verifiedDateString(daysAgo: Double) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: now.addingTimeInterval(-daysAgo * 86_400))
    }

    func testReportedWins() {
        // dislikes > 0 && dislikes > likes → reported, even for ČD source
        XCTAssertEqual(placeStatus(source: "cd", likes: 1, dislikes: 2, lastVerified: nil, now: now), .reported)
        XCTAssertEqual(voteBadge(likes: 1, dislikes: 2, isCd: true), .reported)
    }

    func testOfficialSource() {
        XCTAssertEqual(placeStatus(source: "cd", likes: 0, dislikes: 0, lastVerified: nil, now: now), .official)
    }

    func testRecentlyVerifiedWithinWindow() {
        XCTAssertEqual(
            placeStatus(source: "osm", likes: 0, dislikes: 0, lastVerified: verifiedDateString(daysAgo: 100), now: now),
            .recentlyVerified,
        )
    }

    func testStaleVerificationIsUnverified() {
        XCTAssertEqual(
            placeStatus(source: "osm", likes: 0, dislikes: 0, lastVerified: verifiedDateString(daysAgo: 200), now: now),
            .unverified,
        )
        XCTAssertEqual(
            placeStatus(source: "osm", likes: 0, dislikes: 0, lastVerified: nil, now: now),
            .unverified,
        )
    }

    func testVoteBadgeOrdering() {
        // Verified beats CD_NO_VOTES (any source with votes reads "Ověřeno").
        XCTAssertEqual(voteBadge(likes: 2, dislikes: 0, isCd: true), .verified)
        XCTAssertEqual(voteBadge(likes: 0, dislikes: 0, isCd: true), .cdNoVotes)
        XCTAssertEqual(voteBadge(likes: 0, dislikes: 0, isCd: false), .none)
    }

    func testBadgeLabelsMirrorWebsite() {
        XCTAssertEqual(VoteBadge.reported.label(likes: 1, dislikes: 2), "Nahlášeno nefunguje (2👎)")
        XCTAssertEqual(VoteBadge.verified.label(likes: 4, dislikes: 0), "Ověřeno · 4👍")
        XCTAssertEqual(VoteBadge.cdNoVotes.label(likes: 0, dislikes: 0), "Zatím bez hlasů")
    }

    func testEqualVotesIsNotReported() {
        // dislikes == likes does NOT trigger "reported" (mirror of the website's >).
        XCTAssertEqual(placeStatus(source: "osm", likes: 2, dislikes: 2, lastVerified: nil, now: now), .unverified)
    }
}
