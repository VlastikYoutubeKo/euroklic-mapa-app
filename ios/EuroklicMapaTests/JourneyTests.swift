import XCTest
@testable import EuroklicMapa

final class JourneyTests: XCTestCase {
    private func decode(_ json: String) throws -> JourneyResult {
        try JourneyResult.decode(Data(json.utf8))
    }

    func testDecodesRideAndOldStringWalk() throws {
        let json = """
        {"apiVersion":2,"journeys":[{"departure":"13:43","arrival":"13:49","transfers":0,"totalSeconds":1358,
        "walkToStart":{"meters":386,"seconds":322},"walkFromEnd":{"meters":483,"seconds":402},
        "legs":[{"kind":"ride","line":"582504","lineLabel":"504","mode":"bus","headsign":"Louny",
        "from":{"name":"A","lat":50.1,"lng":14.4},"to":{"name":"B","lat":50.2,"lng":14.5},
        "departure":"13:43","arrival":"13:49","stopCount":4},
        {"kind":"walk","from":"Praha","to":"Praha hl. n.","seconds":274}]}]}
        """
        let result = try decode(json)
        XCTAssertNil(result.errorCode)
        let journey = try XCTUnwrap(result.journeys.first)
        XCTAssertEqual(journey.legs.count, 2)
        guard case .ride(let ride) = journey.legs[0], case .walk(let walk) = journey.legs[1] else {
            return XCTFail("unexpected legs")
        }
        XCTAssertEqual(ride.lineLabel, "504")
        XCTAssertEqual(ride.mode, "bus")
        XCTAssertNil(ride.wheelchairAccessible)
        XCTAssertEqual(walk.from.name, "Praha")
    }

    func testLineLabelFallsBackToLine() throws {
        let json = """
        {"journeys":[{"departure":"8:00","arrival":"8:10","legs":[{"kind":"ride","line":"R 607","mode":"train",
        "from":{"name":"A"},"to":{"name":"B"},"departure":"8:00","arrival":"8:10"}]}]}
        """
        guard case .ride(let ride) = try XCTUnwrap(decode(json).journeys.first?.legs.first) else {
            return XCTFail("no ride")
        }
        XCTAssertEqual(ride.lineLabel, "R 607")
    }

    func testErrorAsStringAndAsObject() throws {
        let old = try decode(#"{"error":"NO_STOPS_NEARBY","side":"to","journeys":[]}"#)
        XCTAssertEqual(old.errorCode, "NO_STOPS_NEARBY")
        XCTAssertEqual(old.side, "to")
        let new = try decode(#"{"error":{"code":"SCHEDULE_NOT_AVAILABLE","message":"x"},"journeys":[]}"#)
        XCTAssertEqual(new.errorCode, "SCHEDULE_NOT_AVAILABLE")
        XCTAssertTrue(new.journeys.isEmpty)
    }

    func testClockHandlesMidnight() {
        XCTAssertEqual(JourneyClock.minutes("24:03"), 24 * 60 + 3)
        // Starší server: příjezd po půlnoci jako 00:03 – bereme jako další den.
        let span = JourneyClock.span(departure: "23:56", arrival: "00:03")
        XCTAssertEqual(span?.arr, 24 * 60 + 3)
        XCTAssertEqual(JourneyClock.format(24 * 60 + 3), "0:03 +1")
        XCTAssertEqual(JourneyClock.format(13 * 60 + 5), "13:05")
    }

    func testMapyLinkWalking() throws {
        let url = try XCTUnwrap(MapyLink.walkingRoute(
            from: .init(latitude: 50.0, longitude: 14.0), to: .init(latitude: 50.1, longitude: 14.1)
        ))
        let text = url.absoluteString
        XCTAssertTrue(text.hasPrefix("https://mapy.com/fnc/v1/route?"))
        XCTAssertTrue(text.contains("routeType=foot_fast"))
        XCTAssertTrue(text.contains("start=14.000000,50.000000"))
    }
}
