import CoreLocation
import SwiftUI

/// Spojení z polohy uživatele k místu: chůze k zastávce, jízda MHD / vlakem, chůze k cíli.
/// Data: plánovač odjezdy.online (`/api/v2/journey`). Chůze je odhad (vzdušná čára), ne trasa po cestách.
@MainActor
final class JourneyModel: ObservableObject {
    enum State {
        case idle
        case loading
        case loaded([Journey])
        case message(String, canRetry: Bool)
    }

    @Published private(set) var state: State = .idle

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "cs_CZ")
        f.dateFormat = "HH:mm"
        return f
    }()

    func load(from origin: CLLocationCoordinate2D?, to target: CLLocationCoordinate2D) async {
        guard let origin else {
            state = .message(L("Pro hledání spojení zapněte polohu, ať víme, odkud vyrážíte."), canRetry: false)
            return
        }
        state = .loading
        let time = Self.timeFormatter.string(from: Date())
        let api = ODAPIClient.shared
        do {
            var result = try await api.journey(
                fromLat: origin.latitude, fromLng: origin.longitude,
                toLat: target.latitude, toLng: target.longitude, time: time
            )
            // Venku ze zástavby bývá nejbližší zastávka dál než 800 m: jednou zkusíme širší okolí.
            if result.errorCode == "NO_STOPS_NEARBY" {
                result = try await api.journey(
                    fromLat: origin.latitude, fromLng: origin.longitude,
                    toLat: target.latitude, toLng: target.longitude, time: time, radius: 2000
                )
            }
            try Task.checkCancellation()
            state = Self.state(for: result)
        } catch is CancellationError {
            return
        } catch let error as ODAPIError {
            state = .message(error.errorDescription ?? L("Spojení se nepodařilo načíst."), canRetry: true)
        } catch {
            if (error as? URLError)?.code == .cancelled { return }
            state = .message(L("Spojení se nepodařilo načíst. Zkontrolujte připojení a zkuste to znovu."), canRetry: true)
        }
    }

    private static func state(for result: JourneyResult) -> State {
        if let code = result.errorCode {
            switch code {
            case "NO_STOPS_NEARBY":
                return .message(result.side == "to"
                    ? L("V okolí místa není do 2 km žádná zastávka.")
                    : L("V okolí vaší polohy není do 2 km žádná zastávka."), canRetry: false)
            case "SCHEDULE_NOT_AVAILABLE":
                return .message(L("Jízdní řády na tento den ještě nejsou k dispozici."), canRetry: false)
            case "NO_SERVICE":
                return .message(L("Dnes nejsou k dispozici žádné spoje."), canRetry: false)
            default:
                return .message(L("Spojení se nepodařilo najít."), canRetry: true)
            }
        }
        if result.journeys.isEmpty {
            return .message(L("Spojení se nepodařilo najít."), canRetry: true)
        }
        return .loaded(result.journeys)
    }
}

struct JourneyView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = JourneyModel()

    let place: PlaceItem
    let origin: CLLocationCoordinate2D?
    /// Otevře systémové Mapy (záloha, když plánovač spojení nenajde).
    let openMaps: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L("Cíl: \(place.name)"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    content
                    disclaimer
                    fallbackLinks
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(L("Spojení MHD"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("Hotovo")) { dismiss() }
                }
            }
        }
        .task { await model.load(from: origin, to: place.coordinate) }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .idle, .loading:
            VStack(spacing: 12) {
                ProgressView()
                Text(L("Hledám spojení…")).font(.headline)
                Text(L("První hledání může trvat až minutu."))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 32)
            .accessibilityElement(children: .combine)
        case .loaded(let journeys):
            ForEach(journeys) { JourneyCard(journey: $0, placeName: place.name) }
        case .message(let text, let canRetry):
            VStack(alignment: .leading, spacing: 12) {
                Label(text, systemImage: "info.circle")
                    .font(.callout)
                if canRetry {
                    Button(L("Zkusit znovu")) { Task { await model.load(from: origin, to: place.coordinate) } }
                        .buttonStyle(.bordered)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)))
        }
    }

    private var disclaimer: some View {
        Text(L("Chůze je odhad vzdušnou čarou, bezbariérovost cesty ani zastávek není ověřená. Jízdní řády: odjezdy.online."))
            .font(.footnote)
            .foregroundStyle(.secondary)
    }

    private var fallbackLinks: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: openMaps) {
                Label(L("Otevřít v Mapách"), systemImage: "map")
            }
            if let url = MapyLink.walkingRoute(from: origin, to: place.coordinate) {
                Link(destination: url) {
                    Label(L("Trasa pěšky v Mapy.com"), systemImage: "figure.walk")
                }
            }
        }
        .font(.callout)
    }
}

/// Odkaz na pěší trasu v Mapy.com (oficiální URL funkce, bez klíče). MHD tímto odkazem předvolit nejde.
enum MapyLink {
    static func walkingRoute(from origin: CLLocationCoordinate2D?, to target: CLLocationCoordinate2D) -> URL? {
        var items = [URLQueryItem]()
        if let origin {
            items.append(URLQueryItem(name: "start", value: String(format: "%.6f,%.6f", origin.longitude, origin.latitude)))
        }
        items.append(URLQueryItem(name: "end", value: String(format: "%.6f,%.6f", target.longitude, target.latitude)))
        items.append(URLQueryItem(name: "routeType", value: "foot_fast"))
        var comps = URLComponents(string: "https://mapy.com/fnc/v1/route")
        comps?.queryItems = items
        return comps?.url
    }
}

private struct JourneyCard: View {
    let journey: Journey
    let placeName: String

    private var times: (leave: String, arrive: String)? {
        guard let span = JourneyClock.span(departure: journey.departure, arrival: journey.arrival) else { return nil }
        let leave = span.dep - JourneyClock.ceilMinutes(journey.walkToStart.seconds)
        let arrive = span.arr + (journey.walkFromEnd.seconds > 0 ? JourneyClock.ceilMinutes(journey.walkFromEnd.seconds) : 0)
        return (JourneyClock.format(leave), JourneyClock.format(arrive))
    }

    private var firstRideFrom: String? {
        for case .ride(let ride) in journey.legs { return ride.from.name }
        return nil
    }

    private var lastRideTo: String? {
        for case .ride(let ride) in journey.legs.reversed() { return ride.to.name }
        return nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            header
            if journey.walkToStart.seconds > 0, let stop = firstRideFrom {
                WalkRow(
                    title: L("Pěšky k zastávce \(stop)"),
                    detail: "\(formatDistance(journey.walkToStart.meters)) · \(journey.walkToStart.seconds.routeDuration)"
                )
            }
            ForEach(Array(journey.legs.enumerated()), id: \.offset) { _, leg in
                switch leg {
                case .ride(let ride): RideRow(ride: ride)
                case .walk(let walk):
                    // Přestup v rámci jedné zastávky („Muzeum → Muzeum“, ~1 min) nic neříká.
                    if !(walk.from.name == walk.to.name && walk.seconds <= 90) {
                        WalkRow(
                            title: L("Přestup pěšky"),
                            detail: "\(walk.from.name) → \(walk.to.name) · \(walk.seconds.routeDuration)"
                        )
                    }
                }
            }
            if journey.walkFromEnd.seconds > 0, let stop = lastRideTo {
                WalkRow(
                    title: L("Pěšky od zastávky \(stop) k místu"),
                    detail: "\(formatDistance(journey.walkFromEnd.meters)) · \(journey.walkFromEnd.seconds.routeDuration)"
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)))
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let times {
                Text(L("Vyjděte v \(times.leave)"))
                    .font(.title3.weight(.semibold))
                Text(L("Na místě v \(times.arrive)") + " · " + transfersText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                Text(transfersText).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var transfersText: String {
        journey.transfers == 0 ? L("Bez přestupu") : L("Přestupů: \(String(journey.transfers))")
    }
}

private struct WalkRow: View {
    let title: String
    let detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "figure.walk")
                .frame(width: 34, height: 28)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline)
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct RideRow: View {
    let ride: JourneyRide

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            LineBadge(label: ride.lineLabel, mode: ride.mode)
                .frame(minWidth: 34, alignment: .leading)
            VStack(alignment: .leading, spacing: 3) {
                if !ride.headsign.isEmpty {
                    Text(ride.headsign).font(.subheadline.weight(.semibold))
                }
                Text("\(ride.from.name) \(ride.departure) → \(ride.to.name) \(ride.arrival)")
                    .font(.footnote)
                if let stops = ride.stopCount {
                    Text(L("Zastávek: \(String(stops))")).font(.footnote).foregroundStyle(.secondary)
                }
                if ride.line != ride.lineLabel {
                    Text(L("Číslo linky: \(ride.line)")).font(.footnote).foregroundStyle(.secondary)
                }
                if let agency = ride.agency, !agency.isEmpty {
                    Text(agency).font(.footnote).foregroundStyle(.secondary)
                }
                if ride.wheelchairAccessible == true {
                    Label(L("Označeno jako bezbariérové"), systemImage: "figure.roll")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Štítek linky ve stylu Odjezdů: čtvercový s malým zaoblením, barva podle druhu vozidla.
private struct LineBadge: View {
    let label: String
    let mode: String?

    private var color: Color {
        switch mode {
        case "train": return Color(odHex: 0x0b7a4b)
        case "tram": return Color(odHex: 0xc4161c)
        case "metro": return Color(odHex: 0x16181d)
        case "bus", "trolleybus", "ferry": return Color(odHex: 0x1d5fd1)
        default: return Color(odHex: 0x5c6470)
        }
    }

    var body: some View {
        Text(label)
            .font(.subheadline.weight(.bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(RoundedRectangle(cornerRadius: 4).fill(color))
            .accessibilityLabel(L("Linka \(label)"))
    }
}
