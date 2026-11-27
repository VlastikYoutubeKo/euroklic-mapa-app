import Foundation

/// Spojení z `GET /api/v2/journey` (app.odjezdy.online). Dekódování je záměrně shovívavé:
/// server zatím mění tvar (např. `from` u pěšího úseku byl řetězec, teď objekt; `error` byl řetězec,
/// teď objekt), appka musí zvládnout oba.
struct JourneyStop: Decodable, Hashable {
    let name: String
    let lat: Double?
    let lng: Double?

    init(name: String, lat: Double? = nil, lng: Double? = nil) {
        self.name = name
        self.lat = lat
        self.lng = lng
    }

    init(from decoder: Decoder) throws {
        if let single = try? decoder.singleValueContainer(), let text = try? single.decode(String.self) {
            self.init(name: text)
            return
        }
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            name: (try? c.decode(String.self, forKey: .name)) ?? "",
            lat: try? c.decode(Double.self, forKey: .lat),
            lng: try? c.decode(Double.self, forKey: .lng)
        )
    }

    private enum CodingKeys: String, CodingKey { case name, lat, lng }
}

struct JourneyWalk: Decodable, Hashable {
    let meters: Double
    let seconds: Double

    init(meters: Double = 0, seconds: Double = 0) {
        self.meters = meters
        self.seconds = seconds
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            meters: (try? c.decode(Double.self, forKey: .meters)) ?? 0,
            seconds: (try? c.decode(Double.self, forKey: .seconds)) ?? 0
        )
    }

    private enum CodingKeys: String, CodingKey { case meters, seconds }
}

struct JourneyRide: Hashable {
    let line: String
    /// Veřejné označení linky (u JDF autobusů poslední 3 číslice), jinak jako `line`.
    let lineLabel: String
    let mode: String?
    let headsign: String
    let from: JourneyStop
    let to: JourneyStop
    let departure: String
    let arrival: String
    let stopCount: Int?
    let agency: String?
    /// `true` = označeno jako bezbariérové; `nil` = neznámé (nikdy neříkáme „není“).
    let wheelchairAccessible: Bool?
    /// Aktuální zpoždění v sekundách (jen když ho server zná), jinak `nil`.
    var delaySeconds: Double? = nil
    /// Zastávky na trase úseku (server je zatím neposílá; až bude, doplní se pokyn „po odjezdu ze zastávky…“).
    var stops: [JourneyStop] = []

    /// Zastávka před výstupní; `nil`, když ji ze seznamu nejde spolehlivě určit.
    var stopBeforeExit: String? {
        guard let i = stops.lastIndex(where: { $0.name == to.name }), i > 0 else { return nil }
        let name = stops[i - 1].name
        return name.isEmpty || name == from.name && i - 1 != 0 ? nil : name
    }

    /// Tlačítko STOP dává smysl jen v autobusu, trolejbusu a tramvaji; u neznámého druhu mlčíme.
    var mayNeedStopButton: Bool { ["bus", "trolleybus", "tram"].contains(mode ?? "") }
}

struct JourneyTransferWalk: Hashable {
    let from: JourneyStop
    let to: JourneyStop
    let seconds: Double
}

enum JourneyLeg: Decodable, Hashable {
    case ride(JourneyRide)
    case walk(JourneyTransferWalk)

    private enum CodingKeys: String, CodingKey {
        case kind, line, lineLabel, mode, headsign, from, to, departure, arrival, stopCount, seconds
        case agency, wheelchairAccessible, delaySeconds, stops
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let kind = (try? c.decode(String.self, forKey: .kind)) ?? "ride"
        let from = (try? c.decode(JourneyStop.self, forKey: .from)) ?? JourneyStop(name: "")
        let to = (try? c.decode(JourneyStop.self, forKey: .to)) ?? JourneyStop(name: "")
        if kind == "walk" {
            let seconds = (try? c.decode(Double.self, forKey: .seconds)) ?? 0
            self = .walk(JourneyTransferWalk(from: from, to: to, seconds: seconds))
            return
        }
        let line = (try? c.decode(String.self, forKey: .line)) ?? ""
        let label = (try? c.decode(String.self, forKey: .lineLabel)) ?? ""
        self = .ride(JourneyRide(
            line: line,
            lineLabel: label.isEmpty ? line : label,
            mode: try? c.decode(String.self, forKey: .mode),
            headsign: (try? c.decode(String.self, forKey: .headsign)) ?? "",
            from: from,
            to: to,
            departure: (try? c.decode(String.self, forKey: .departure)) ?? "",
            arrival: (try? c.decode(String.self, forKey: .arrival)) ?? "",
            stopCount: try? c.decode(Int.self, forKey: .stopCount),
            agency: try? c.decode(String.self, forKey: .agency),
            wheelchairAccessible: try? c.decode(Bool.self, forKey: .wheelchairAccessible),
            delaySeconds: try? c.decode(Double.self, forKey: .delaySeconds),
            stops: (try? c.decode([JourneyStop].self, forKey: .stops)) ?? []
        ))
    }
}

struct Journey: Decodable, Hashable, Identifiable {
    let departure: String
    let arrival: String
    let transfers: Int
    let totalSeconds: Double
    let walkToStart: JourneyWalk
    let walkFromEnd: JourneyWalk
    let legs: [JourneyLeg]

    var id: String { "\(departure)-\(arrival)-\(legs.count)" }

    private enum CodingKeys: String, CodingKey {
        case departure, arrival, transfers, totalSeconds, walkToStart, walkFromEnd, legs
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        departure = (try? c.decode(String.self, forKey: .departure)) ?? ""
        arrival = (try? c.decode(String.self, forKey: .arrival)) ?? ""
        transfers = (try? c.decode(Int.self, forKey: .transfers)) ?? 0
        totalSeconds = (try? c.decode(Double.self, forKey: .totalSeconds)) ?? 0
        walkToStart = (try? c.decode(JourneyWalk.self, forKey: .walkToStart)) ?? JourneyWalk()
        walkFromEnd = (try? c.decode(JourneyWalk.self, forKey: .walkFromEnd)) ?? JourneyWalk()
        legs = (try? c.decode([JourneyLeg].self, forKey: .legs)) ?? []
    }
}

/// Odpověď včetně „bez výsledku“ (HTTP 200 s `error`, buď řetězec, nebo objekt `{code}`).
struct JourneyResult {
    let journeys: [Journey]
    let errorCode: String?
    /// `from` | `to` u `NO_STOPS_NEARBY`.
    let side: String?

    private struct ErrorValue: Decodable {
        let code: String?
        let side: String?

        init(from decoder: Decoder) throws {
            if let single = try? decoder.singleValueContainer(), let text = try? single.decode(String.self) {
                code = text
                side = nil
                return
            }
            let c = try decoder.container(keyedBy: Keys.self)
            code = try? c.decode(String.self, forKey: .code)
            side = try? c.decode(String.self, forKey: .side)
        }

        private enum Keys: String, CodingKey { case code, side }
    }

    private struct Raw: Decodable {
        let journeys: [Journey]?
        let side: String?
        let error: ErrorValue?
    }

    static func decode(_ data: Data) throws -> JourneyResult {
        guard let raw = try? JSONDecoder().decode(Raw.self, from: data) else { throw ODAPIError.badData }
        return JourneyResult(journeys: raw.journeys ?? [], errorCode: raw.error?.code, side: raw.error?.side ?? raw.side)
    }
}

/// Čas `HH:MM` z plánovače. Hodiny můžou být ≥ 24 (po půlnoci); starší verze serveru vracela po půlnoci
/// `00:03` – příjezd menší než odjezd proto bereme jako další den.
enum JourneyClock {
    static func minutes(_ text: String) -> Int? {
        let parts = text.split(separator: ":")
        guard parts.count >= 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }

    /// Odjezd a příjezd jako minuty od půlnoci dne dotazu, příjezd vždy po odjezdu.
    static func span(departure: String, arrival: String) -> (dep: Int, arr: Int)? {
        guard let dep = minutes(departure), var arr = minutes(arrival) else { return nil }
        if arr < dep { arr += 24 * 60 }
        return (dep, arr)
    }

    static func format(_ minutes: Int) -> String {
        let day = minutes / (24 * 60)
        let inDay = minutes % (24 * 60)
        let text = String(format: "%d:%02d", inDay / 60, inDay % 60)
        return day > 0 ? text + " +1" : text
    }

    /// Čas ze serveru pro zobrazení („24:03“ → „0:03 +1“); neznámý tvar zůstane beze změny.
    static func display(_ text: String) -> String {
        minutes(text).map(format) ?? text
    }

    static func ceilMinutes(_ seconds: Double) -> Int { max(1, Int((seconds / 60).rounded(.up))) }
}

/// Pokyny krok za krokem v obyčejných větách. Nic si nedomýšlí: tlačítko STOP zmiňuje jen u autobusu,
/// trolejbusu a tramvaje a „po odjezdu ze zastávky X“ jen když seznam zastávek X opravdu obsahuje.
enum JourneyGuide {
    static func steps(for journey: Journey) -> [String] {
        var result: [String] = []
        let firstRide = journey.legs.lazy.compactMap { leg -> JourneyRide? in
            if case .ride(let ride) = leg { return ride }
            return nil
        }.first
        if journey.walkToStart.seconds > 0, let stop = firstRide?.from.name {
            result.append(L("Dojděte pěšky k zastávce \(stop) (\(formatDistance(journey.walkToStart.meters)))."))
        }
        for leg in journey.legs {
            switch leg {
            case .ride(let ride):
                let departure = JourneyClock.display(ride.departure)
                if ride.headsign.isEmpty {
                    result.append(L("Na zastávce \(ride.from.name) nastupte do linky \(ride.lineLabel), odjezd \(departure)."))
                } else {
                    result.append(L("Na zastávce \(ride.from.name) nastupte do linky \(ride.lineLabel) směr \(ride.headsign), odjezd \(departure)."))
                }
                if ride.mayNeedStopButton {
                    if let previous = ride.stopBeforeExit {
                        result.append(L("Po odjezdu ze zastávky \(previous) stiskněte tlačítko STOP, pokud je vaše zastávka na znamení."))
                    } else {
                        result.append(L("Je-li vaše výstupní zastávka na znamení, stiskněte před ní včas tlačítko STOP."))
                    }
                }
                let arrival = JourneyClock.display(ride.arrival)
                if let count = ride.stopCount, count > 0 {
                    result.append(L("Vystupte na zastávce \(ride.to.name) (\(String(count)). zastávka, příjezd \(arrival))."))
                } else {
                    result.append(L("Vystupte na zastávce \(ride.to.name), příjezd \(arrival)."))
                }
            case .walk(let walk):
                if !(walk.from.name == walk.to.name && walk.seconds <= 90) {
                    result.append(L("Přejděte pěšky na zastávku \(walk.to.name) (\(walk.seconds.routeDuration))."))
                }
            }
        }
        if journey.walkFromEnd.seconds > 0 {
            result.append(L("Dojděte pěšky k cíli (\(formatDistance(journey.walkFromEnd.meters)))."))
        }
        return result
    }
}
