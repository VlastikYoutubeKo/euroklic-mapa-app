import XCTest
@testable import EuroklicMapa

final class UpdateCheckerTests: XCTestCase {
    func testNewerPatchMinorMajor() {
        XCTAssertTrue(UpdateChecker.isNewer("1.4.1", than: "1.4.0"))
        XCTAssertTrue(UpdateChecker.isNewer("1.5.0", than: "1.4.9"))
        XCTAssertTrue(UpdateChecker.isNewer("2.0.0", than: "1.9.9"))
        XCTAssertTrue(UpdateChecker.isNewer("1.10.0", than: "1.9.0"))
    }

    func testSameOrOlderIsNotNewer() {
        XCTAssertFalse(UpdateChecker.isNewer("1.4.0", than: "1.4.0"))
        XCTAssertFalse(UpdateChecker.isNewer("1.3.9", than: "1.4.0"))
        XCTAssertFalse(UpdateChecker.isNewer("1.4", than: "1.4.0"))
    }

    func testBetaBuildsNeverGetPrompted() {
        XCTAssertFalse(UpdateChecker.isNewer("1.4.0", than: "1.4.0-beta.1"))
        XCTAssertFalse(UpdateChecker.isNewer("9.9.9", than: "1.4.0-beta.3"))
    }

    func testGarbageIsIgnored() {
        XCTAssertFalse(UpdateChecker.isNewer("", than: "1.4.0"))
        XCTAssertFalse(UpdateChecker.isNewer("abc", than: "1.4.0"))
        XCTAssertFalse(UpdateChecker.isNewer("1.5.0", than: "unknown"))
    }

    func testLeadingVAndShortFormsWork() {
        XCTAssertTrue(UpdateChecker.isNewer("v1.5.0", than: "1.4.0"))
        XCTAssertTrue(UpdateChecker.isNewer("1.5", than: "1.4.9"))
    }
}
