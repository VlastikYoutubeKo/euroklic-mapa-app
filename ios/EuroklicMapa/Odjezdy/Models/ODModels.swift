import CoreLocation
import Foundation

// MARK: - Tolerantní dekódování

extension KeyedDecodingContainer {
    /// Řetězec; číslo se převede na text (id nemá být číslo, ale nikdy nevíme).
    func odString(_ key: Key) -> String? {
        if let s = try? decode(String.self, forKey: key) { return s }
        if let n = try? decode(Int.self, forKey: key) { return String(n) }
        if let d = try? decode(Double.self, forKey: key) { return String(d) }
        return nil
    }

    /// Řetězec, nebo objekt s polem `name`.
    func odStringOrName(_ key: Key) -> String? {
        if let s = odString(key) { return s }
        if let o = try? decode(ODNameObject.self, forKey: key) { return o.name }
        return nil
    }

    func odDouble(_ key: Key) -> Double? {
        if let d = try? decode(Double.self, forKey: key) { return d }
        if let s = try? decode(String.self, forKey: key) { return Double(s.replacingOccurrences(of: ",", with: ".")) }
        return nil
    }

    func odInt(_ key: Key) -> Int? {
        if let i = try? decode(Int.self, forKey: key) { return i }
        if let d = try? decode(Double.self, forKey: key), d.isFinite { return Int(d.rounded()) }
        if let s = try? decode(String.self, forKey: key) { return Int(s) }
        return nil
    }

    func odBool(_ key: Key) -> Bool? {
        if let b = try? decode(Bool.self, forKey: key) { return b }
        if let i = try? decode(Int.self, forKey: key) { return i != 0 }
        if let s = try? decode(String.self, forKey: key) {
            switch s.lowercased() {
            case "true", "1", "yes", "ano": return true
            case "false", "0", "no", "ne": return false
            default: return nil
            }
        }
        return nil
    }

    func odStringArray(_ key: Key) -> [String] {
        if let a = try? decode([ODLossy<ODFlexString>].self, forKey: key) {
            return a.compactMap { $0.value?.text }
        }
        return []
    }
}

/// Prvek pole, jehož chybné dekódování neshodí celý seznam.
struct ODLossy<T: Decodable>: Decodable {
    let value: T?
    init(from decoder: Decoder) throws {
        value = try? T(from: decoder)
    }
}

struct ODNameObject: Decodable {
    let name: String?
    enum CodingKeys: String, CodingKey { case name }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = c.odString(.name)
    }
}

/// Text z řetězce, čísla nebo objektu s `name`.
struct ODFlexString: Decodable {
    let text: String
    init(from decoder: Decoder) throws {
        let single = try decoder.singleValueContainer()
        if let s = try? single.decode(String.self) { text = s; return }
        if let n = try? single.decode(Int.self) { text = String(n); return }
        if let o = try? ODNameObject(from: decoder), let n = o.name { text = n; return }
        throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "ODFlexString"))
    }
}

// MARK: - Typ dopravy

enum ODTransportType: String, CaseIterable, Hashable {
    case bus, tram, trolleybus, subway, train, ferry, other

    init(raw: String?) {
        switch (raw ?? "").lowercased() {
        case "bus": self = .bus
        case "tram": self = .tram
        case "trolleybus": self = .trolleybus
        case "subway", "metro": self = .subway
        case "train", "rail": self = .train
        case "ferry", "boat": self = .ferry
        default: self = .other
        }
    }

    var label: String {
        switch self {
        case .bus: return "Autobus"
        case .tram: return "Tramvaj"
        case .trolleybus: return "Trolejbus"
        case .subway: return "Metro"
        case .train: return "Vlak"
        case .ferry: return "Přívoz"
        case .other: return "Spoj"
        }
    }

    var symbol: String {
        switch self {
        case .bus, .trolleybus, .other: return "bus.fill"
        case .tram: return "tram.fill"
        case .subway: return "tram.tunnel.fill"
        case .train: return "train.side.front.car"
        case .ferry: return "ferry.fill"
        }
    }
}

/// Barevná třída štítku (jako `lineClass` na webu).
enum ODLineClass: Hashable {
    case bus, train, tram, night

    static func of(type: ODTransportType, line: String) -> ODLineClass {
        let t = line.trimmingCharacters(in: .whitespaces)
        if t.count >= 2, t.first == "N" || t.first == "n", t.dropFirst().first?.isNumber == true { return .night }
        switch type {
        case .train: return .train
        case .tram, .subway: return .tram
        default: return .bus
        }
    }
}

// MARK: - Vozidlo

struct ODVehicle: Identifiable, Hashable, Decodable {
    let id: String
    let provider: String
    let type: ODTransportType
    let line: String
    let destination: String?
    let lat: Double
    let lng: Double
    let bearing: Double?
    let updatedAt: Date?
    let currentStop: String?
    let nextStop: String?
    let delayMinutes: Int?
    let offline: Bool
    let tripId: String?
    let spz: String?
    let evc: String?
    let wheelchair: Bool?
    /// Stupně za sekundu (lng, lat) – jen pro vizuální dopočet pohybu.
    let vx: Double?
    let vy: Double?
    let mergedFrom: [String]

    enum CodingKeys: String, CodingKey {
        case id, provider, type, line, destination, location, updatedAt, currentStop, nextStop, delay, attributes, motion, mergedFrom
    }
    enum LocKeys: String, CodingKey { case lat, lng, bearing }
    enum DelayKeys: String, CodingKey { case minutes, offline }
    enum AttrKeys: String, CodingKey { case spz, evc, wheelchair, tripId }
    enum MotionKeys: String, CodingKey { case vx, vy }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard let id = c.odString(.id), !id.isEmpty else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id")
        }
        let loc = try c.nestedContainer(keyedBy: LocKeys.self, forKey: .location)
        guard let la = loc.odDouble(.lat), let lo = loc.odDouble(.lng), la.isFinite, lo.isFinite,
              abs(la) <= 90, abs(lo) <= 180 else {
            throw DecodingError.dataCorruptedError(forKey: .location, in: c, debugDescription: "location")
        }
        self.id = id
        self.lat = la
        self.lng = lo
        self.bearing = loc.odDouble(.bearing)
        self.provider = c.odString(.provider) ?? ""
        self.type = ODTransportType(raw: c.odString(.type))
        self.line = (c.odString(.line) ?? "").trimmingCharacters(in: .whitespaces)
        self.destination = c.odStringOrName(.destination)
        self.updatedAt = ODTime.parse(c.odString(.updatedAt))
        self.currentStop = c.odStringOrName(.currentStop)
        self.nextStop = c.odStringOrName(.nextStop)
        self.mergedFrom = c.odStringArray(.mergedFrom)

        if let d = try? c.nestedContainer(keyedBy: DelayKeys.self, forKey: .delay) {
            self.delayMinutes = d.odInt(.minutes)
            self.offline = d.odBool(.offline) ?? false
        } else {
            self.delayMinutes = nil
            self.offline = false
        }
        if let a = try? c.nestedContainer(keyedBy: AttrKeys.self, forKey: .attributes) {
            self.spz = a.odString(.spz)
            self.evc = a.odString(.evc)
            self.wheelchair = a.odBool(.wheelchair)
            self.tripId = a.odString(.tripId)
        } else {
            self.spz = nil
            self.evc = nil
            self.wheelchair = nil
            self.tripId = nil
        }
        if let m = try? c.nestedContainer(keyedBy: MotionKeys.self, forKey: .motion) {
            let x = m.odDouble(.vx)
            let y = m.odDouble(.vy)
            // Rozumný strop (0,001 °/s ≈ 100 m/s), jinak je to chybná hodnota.
            if let x, let y, x.isFinite, y.isFinite, abs(x) < 0.001, abs(y) < 0.001 {
                self.vx = x
                self.vy = y
            } else {
                self.vx = nil
                self.vy = nil
            }
        } else {
            self.vx = nil
            self.vy = nil
        }
    }

    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lng) }

    /// Linka "N/A" nebo prázdná = mimo provoz / v depu.
    var isInService: Bool {
        let l = line.uppercased()
        return !(l.isEmpty || l == "N/A" || l == "-")
    }

    var lineClass: ODLineClass { ODLineClass.of(type: type, line: line) }

    /// Text na štítku mapy.
    var badgeText: String {
        if isInService { return line }
        if let evc, !evc.isEmpty { return evc }
        return "–"
    }

    var delayText: String? {
        guard isInService, let m = delayMinutes else { return nil }
        return m <= 0 ? "včas" : "+\(m) min"
    }

    var accessibilityText: String {
        var parts: [String] = []
        if isInService {
            parts.append("\(type.label) linka \(line)")
        } else {
            parts.append("\(type.label) mimo provoz")
        }
        if let destination, !destination.isEmpty { parts.append("směr \(destination)") }
        if let d = delayText { parts.append(d == "včas" ? "jede včas" : "zpoždění \(delayMinutes ?? 0) minut") }
        return parts.joined(separator: ", ")
    }
}

// MARK: - Zastávka

struct ODStop: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let lat: Double
    let lng: Double
    let provider: String

    init(id: String, name: String, lat: Double, lng: Double, provider: String) {
        self.id = id
        self.name = name
        self.lat = lat
        self.lng = lng
        self.provider = provider
    }

    enum CodingKeys: String, CodingKey { case id, name, lat, lng, provider }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard let id = c.odString(.id), !id.isEmpty, let name = c.odString(.name), !name.isEmpty,
              let la = c.odDouble(.lat), let lo = c.odDouble(.lng), la.isFinite, lo.isFinite else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "stop")
        }
        self.id = id
        self.name = name
        self.lat = la
        self.lng = lo
        self.provider = c.odString(.provider) ?? ""
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(lat, forKey: .lat)
        try c.encode(lng, forKey: .lng)
        try c.encode(provider, forKey: .provider)
    }

    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lng) }
}

// MARK: - Odjezd

struct ODDeparture: Identifiable, Hashable, Decodable {
    let id: String
    let stopId: String?
    let provider: String
    let type: ODTransportType
    let line: String
    let destination: String
    let platform: String?
    let planned: Date
    let expected: Date?
    let delayMinutes: Int?
    let cancelled: Bool
    let lowFloor: Bool?
    let terminatesHere: Bool
    let origin: String?
    let viaStops: [String]
    let operatorName: String?

    enum CodingKeys: String, CodingKey {
        case id, stopId, provider, type, line, destination, platform, plannedTime, expectedTime
        case delayMinutes, cancelled, lowFloor, terminatesHere, origin, viaStops
        case operatorName = "operator"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        guard let planned = ODTime.parse(c.odString(.plannedTime)) else {
            throw DecodingError.dataCorruptedError(forKey: .plannedTime, in: c, debugDescription: "plannedTime")
        }
        self.planned = planned
        self.expected = ODTime.parse(c.odString(.expectedTime))
        self.line = (c.odString(.line) ?? "").trimmingCharacters(in: .whitespaces)
        self.destination = c.odStringOrName(.destination) ?? ""
        self.provider = c.odString(.provider) ?? ""
        self.type = ODTransportType(raw: c.odString(.type))
        self.stopId = c.odString(.stopId)
        self.platform = c.odString(.platform)
        self.delayMinutes = c.odInt(.delayMinutes)
        self.cancelled = c.odBool(.cancelled) ?? false
        self.lowFloor = c.odBool(.lowFloor)
        self.terminatesHere = c.odBool(.terminatesHere) ?? false
        self.origin = c.odStringOrName(.origin)
        self.viaStops = c.odStringArray(.viaStops)
        self.operatorName = c.odString(.operatorName)
        self.id = c.odString(.id) ?? "\(provider)-\(line)-\(Int(planned.timeIntervalSince1970))"
    }

    /// Kdy spoj skutečně odjede (jako `realTime` na webu).
    var realTime: Date {
        if let expected { return expected }
        return planned.addingTimeInterval(Double(max(0, delayMinutes ?? 0)) * 60)
    }

    var lineClass: ODLineClass { ODLineClass.of(type: type, line: line) }

    /// Popisek dopravce pro chips.
    var systemLabel: String { ODProvider.label(provider) }
}

enum ODProvider {
    static func label(_ raw: String) -> String {
        switch raw.uppercased() {
        case "": return "Ostatní"
        case "PID": return "PID"
        case "DUK": return "DÚK"
        case "IDSJMK": return "IDS JMK"
        case "ODIS": return "ODIS"
        case "IDOL": return "IDOL"
        case "ZLIN": return "Zlín"
        case "JIKORD": return "JIKORD"
        case "IDPK": return "IDPK"
        case "IDSOK": return "IDSOK"
        case "ARRIVA": return "Arriva"
        case "DPMD": return "DPMD"
        case "CZPTT": return "Vlaky SŽ"
        case "ČD", "CD": return "České dráhy"
        case "SZ": return "Správa železnic"
        default: return raw
        }
    }
}

// MARK: - Trasa a vizitka vozidla

struct ODRouteStop: Identifiable, Hashable {
    let id: Int
    let name: String
    let time: Date?
    let rawTime: String?
    let lat: Double?
    let lng: Double?
    let stopId: String?
}

struct ODVehicleRoute: Equatable {
    var available: Bool
    var traveled: [CLLocationCoordinate2D]
    var remaining: [CLLocationCoordinate2D]
    var stops: [ODRouteStop]
    var destination: String?

    static let unavailable = ODVehicleRoute(available: false, traveled: [], remaining: [], stops: [], destination: nil)

    static func == (a: ODVehicleRoute, b: ODVehicleRoute) -> Bool {
        a.available == b.available && a.traveled.count == b.traveled.count
            && a.remaining.count == b.remaining.count && a.stops == b.stops
    }
}

extension ODVehicleRoute: Decodable {
    enum CodingKeys: String, CodingKey { case available, traveled, remaining, stops, timetable, destination }

    private struct RawStop: Decodable {
        let id: String?
        let name: String?
        let lat: Double?
        let lng: Double?
        let time: String?
        enum CodingKeys: String, CodingKey { case id, name, lat, lng, time }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = c.odString(.id)
            name = c.odStringOrName(.name)
            lat = c.odDouble(.lat)
            lng = c.odDouble(.lng)
            time = c.odString(.time)
        }
    }

    private static func coords(_ pairs: [[Double]]?) -> [CLLocationCoordinate2D] {
        (pairs ?? []).compactMap { p in
            guard p.count >= 2, p[0].isFinite, p[1].isFinite, abs(p[0]) <= 90, abs(p[1]) <= 180 else { return nil }
            return CLLocationCoordinate2D(latitude: p[0], longitude: p[1])
        }
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let avail = c.odBool(.available) ?? false
        let tr = try? c.decode([[Double]].self, forKey: .traveled)
        let rem = try? c.decode([[Double]].self, forKey: .remaining)
        var raw: [RawStop] = ((try? c.decode([ODLossy<RawStop>].self, forKey: .stops)) ?? []).compactMap { $0.value }
        if raw.isEmpty {
            raw = ((try? c.decode([ODLossy<RawStop>].self, forKey: .timetable)) ?? []).compactMap { $0.value }
        }
        let now = Date()
        let mapped: [ODRouteStop] = raw.enumerated().compactMap { index, s in
            guard let name = s.name, !name.isEmpty else { return nil }
            return ODRouteStop(
                id: index, name: name, time: ODTime.parse(s.time, now: now), rawTime: s.time,
                lat: s.lat, lng: s.lng, stopId: s.id
            )
        }
        self.available = avail
        self.traveled = Self.coords(tr)
        self.remaining = Self.coords(rem)
        self.stops = mapped
        self.destination = c.odStringOrName(.destination)
    }
}

struct ODVehicleInfo: Decodable, Equatable {
    var available: Bool
    var pending: Bool
    var manufacturer: String?
    var model: String?
    var year: String?
    var carrier: String?
    var depot: String?
    var fleetNo: String?
    var plate: String?
    var equipment: String?
    var note: String?

    enum CodingKeys: String, CodingKey { case available, pending, info }
    enum InfoKeys: String, CodingKey {
        case manufacturer, model, year, carrier, depot, fleetNo, plate, equipment, note
    }

    static let none = ODVehicleInfo(available: false, pending: false)

    init(available: Bool, pending: Bool) {
        self.available = available
        self.pending = pending
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        available = c.odBool(.available) ?? false
        pending = c.odBool(.pending) ?? false
        if let i = try? c.nestedContainer(keyedBy: InfoKeys.self, forKey: .info) {
            manufacturer = i.odString(.manufacturer)
            model = i.odString(.model)
            year = i.odString(.year)
            carrier = i.odString(.carrier)
            depot = i.odString(.depot)
            fleetNo = i.odString(.fleetNo)
            plate = i.odString(.plate)
            if let s = i.odString(.equipment) {
                equipment = s
            } else {
                let list = i.odStringArray(.equipment)
                equipment = list.isEmpty ? nil : list.joined(separator: ", ")
            }
            note = i.odString(.note)
        }
    }

    var isEmpty: Bool {
        [manufacturer, model, year, carrier, depot, fleetNo, plate, equipment, note]
            .allSatisfy { ($0 ?? "").isEmpty }
    }
}

struct ODDelayAverage: Decodable {
    let avgDelay: Double?
    let tripCount: Int

    enum CodingKeys: String, CodingKey { case avgDelay = "avg_delay", tripCount = "trip_count" }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        avgDelay = c.odDouble(.avgDelay)
        tripCount = c.odInt(.tripCount) ?? 0
    }
}

// MARK: - Aktuality

struct ODAlert: Identifiable, Hashable, Decodable {
    let id: String
    let provider: String
    let title: String
    let summary: String?
    let details: String?
    let validFrom: Date?
    let validTo: Date?
    let lines: [String]
    let stops: [String]
    let lat: Double?
    let lng: Double?
    let category: String?

    enum CodingKeys: String, CodingKey {
        case id, provider, title, description, summary, validFrom, validTo, lines, stops, lat, lng, category
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let title = c.odString(.title) ?? c.odString(.summary) ?? ""
        guard !title.isEmpty else {
            throw DecodingError.dataCorruptedError(forKey: .title, in: c, debugDescription: "title")
        }
        self.title = title
        self.id = c.odString(.id) ?? title
        self.provider = c.odString(.provider) ?? ""
        self.summary = c.odString(.summary)
        self.details = c.odString(.description)
        self.validFrom = ODTime.parse(c.odString(.validFrom))
        self.validTo = ODTime.parse(c.odString(.validTo))
        self.lines = c.odStringArray(.lines)
        self.stops = c.odStringArray(.stops)
        self.lat = c.odDouble(.lat)
        self.lng = c.odDouble(.lng)
        self.category = c.odString(.category)
    }

    func isActive(at now: Date = Date()) -> Bool {
        if let validTo, validTo < now { return false }
        return true
    }

    var categoryLabel: String? {
        switch (category ?? "").lowercased() {
        case "": return nil
        case "closure": return "Výluka"
        case "replacement": return "Náhradní doprava"
        case "delay": return "Zpoždění"
        default: return category
        }
    }
}
