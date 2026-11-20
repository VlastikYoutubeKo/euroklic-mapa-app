import XCTest
@testable import EuroklicMapa

final class ChangelogTests: XCTestCase {
    private let sample = """
    # Changelog

    Úvod, který se ignoruje.

    ## 1.1.0 — 2026-12-01
    - Nová věc
    - Druhá věc s **tučným** textem

    ## 1.0.0 — 2026-11-19
    - První verze
    """

    func testParsesVersionsDatesAndItems() {
        let entries = Changelog.parse(sample)
        XCTAssertEqual(entries.map(\.version), ["1.1.0", "1.0.0"])
        XCTAssertEqual(entries[0].date, "2026-12-01")
        XCTAssertEqual(entries[0].items.count, 2)
        XCTAssertEqual(entries[1].items, ["První verze"])
    }

    func testNormalizedTreatsTrailingZerosAsSameRelease() {
        XCTAssertEqual(Changelog.normalized("1.0"), Changelog.normalized("1.0.0"))
        XCTAssertNotEqual(Changelog.normalized("1.1"), Changelog.normalized("1.0.0"))
    }

    func testEntryLookupByBundleVersion() {
        let entries = Changelog.parse(sample)
        XCTAssertEqual(Changelog.entry(for: "1.0", in: entries)?.version, "1.0.0")
        XCTAssertNil(Changelog.entry(for: "2.0", in: entries))
    }

    func testPlatformTagsFilterAndroidAndStripIOS() {
        let text = """
        ## 2.0.0 — 2027-01-01
        - [Android] Jen pro Android
        - [iOS] Jen pro iOS
        - Pro obě
        ## 1.9.0 — 2026-12-31
        - [Android] Také jen Android
        """
        let entries = Changelog.parse(text)
        XCTAssertEqual(entries.map(\.version), ["2.0.0"])
        XCTAssertEqual(entries[0].items, ["Jen pro iOS", "Pro obě"])
    }
}
