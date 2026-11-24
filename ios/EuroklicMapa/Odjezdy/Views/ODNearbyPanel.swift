import CoreLocation
import SwiftUI

/// Zastávka v okolí s nejbližšími odjezdy (pro spodní panel na mapě).
struct ODNearbyEntry: Identifiable {
    let stop: ODStop
    let distanceM: Int
    let departures: [ODDeparture]
    var id: String { stop.id }

    /// Převažující typ dopravy – podle něj je kulatá ikona řádku.
    var type: ODTransportType {
        var counts: [ODTransportType: Int] = [:]
        for d in departures { counts[d.type, default: 0] += 1 }
        return counts.max { $0.value < $1.value }?.key ?? .bus
    }
}

/// Zastávky kolem polohy a jejich nejbližší odjezdy; obnova po ~30 s, dokud je panel vidět.
@MainActor
final class ODNearbyViewModel: ObservableObject {
    @Published private(set) var entries: [ODNearbyEntry] = []
    @Published private(set) var isLoading = true

    private let api = ODAPIClient.shared
    private static let maxStops = 6

    nonisolated init() {}

    func run(around location: CLLocation) async {
        while !Task.isCancelled {
            await load(around: location)
            try? await Task.sleep(for: .seconds(30))
        }
    }

    func load(around location: CLLocation) async {
        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude
        do {
            let list = try await api.stops(
                minLat: lat - 0.006, maxLat: lat + 0.006,
                minLng: lng - 0.009, maxLng: lng + 0.009
            )
            var seen = Set<String>()
            let nearest = list
                .map { ODNearbyStop(stop: $0, distanceM: Int(location.distance(from: CLLocation(latitude: $0.lat, longitude: $0.lng)))) }
                .sorted { $0.distanceM < $1.distanceM }
                .filter { seen.insert($0.stop.name).inserted }
                .prefix(Self.maxStops)

            var out: [ODNearbyEntry] = []
            let cutoff = Date().addingTimeInterval(-60)
            for n in nearest {
                if Task.isCancelled { return }
                let deps = (try? await api.departures(stopId: n.stop.id, limit: 12, minutes: 180)) ?? []
                let upcoming = deps
                    .filter { $0.realTime >= cutoff && !$0.cancelled }
                    .sorted { $0.realTime < $1.realTime }
                out.append(ODNearbyEntry(stop: n.stop, distanceM: n.distanceM, departures: Array(upcoming.prefix(6))))
            }
            entries = out
        } catch is CancellationError {
            return
        } catch {
            // Panel je doplněk mapy; bez dat zůstane předchozí stav.
        }
        isLoading = false
    }
}

/// Spodní panel na mapě: pole „Kam to bude?“ a zastávky v okolí s odjezdy (v duchu appky Catch).
/// Tažením nahoru/dolů nebo klepnutím na úchyt se rozbalí a sbalí.
struct ODNearbyPanel: View {
    @Environment(\.odColors) private var c
    @ObservedObject var location: ODLocationProvider
    let expandedHeight: CGFloat
    let onSearch: () -> Void
    let onOpenStop: (ODStop) -> Void

    @StateObject private var vm = ODNearbyViewModel()
    @State private var expanded = false

    private static let collapsedHeight: CGFloat = 128

    private var locationKey: String {
        guard let l = location.location else { return "none" }
        return String(format: "%.3f,%.3f", l.coordinate.latitude, l.coordinate.longitude)
    }

    var body: some View {
        VStack(spacing: 0) {
            handle
            searchField
                .padding(.horizontal, 16)
            if expanded {
                list
            }
        }
        .frame(height: expanded ? expandedHeight : Self.collapsedHeight, alignment: .top)
        .frame(maxWidth: .infinity)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 20, topTrailingRadius: 20, style: .continuous)
                .fill(c.surface)
                .shadow(color: Color.black.opacity(0.25), radius: 8, y: -2)
                .ignoresSafeArea(edges: .bottom)
        )
        .animation(.easeInOut(duration: 0.22), value: expanded)
        .task(id: locationKey) {
            if let l = location.location { await vm.run(around: l) }
        }
        .gesture(
            DragGesture(minimumDistance: 12).onEnded { value in
                if value.translation.height < -30 { expanded = true }
                if value.translation.height > 30 { expanded = false }
            }
        )
    }

    private var handle: some View {
        Button {
            expanded.toggle()
        } label: {
            Capsule()
                .fill(c.muted.opacity(0.6))
                .frame(width: 40, height: 5)
                .frame(maxWidth: .infinity, minHeight: 28)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(expanded ? L("Sbalit zastávky v okolí") : L("Rozbalit zastávky v okolí"))
    }

    private var searchField: some View {
        Button(action: onSearch) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.body.weight(.semibold))
                    .accessibilityHidden(true)
                Text("Kam to bude?")
                    .font(.body.weight(.semibold))
                Spacer(minLength: 0)
            }
            .foregroundStyle(c.text)
            .padding(.horizontal, 14)
            .frame(minHeight: 48)
            .background(c.surface2, in: Capsule())
            .overlay(Capsule().stroke(c.line, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Otevře hledání zastávky nebo vozidla")
    }

    // MARK: Seznam

    private var list: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("V okolí")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(c.text)
                    .padding(.horizontal, 16)
                    .padding(.top, 18)
                    .padding(.bottom, 6)
                    .accessibilityAddTraits(.isHeader)
                content
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if location.location == nil {
            VStack(spacing: 12) {
                Text("Zastávky v okolí ukážeme, když povolíte polohu.")
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
                    .multilineTextAlignment(.center)
                if !location.isDenied {
                    Button {
                        location.request()
                    } label: {
                        Label("Zobrazit zastávky v okolí", systemImage: "location")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(c.inkText)
                            .padding(.horizontal, 16)
                            .frame(minHeight: 44)
                            .background(c.ink, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(24)
        } else if vm.entries.isEmpty {
            if vm.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(32)
            } else {
                Text("V okolí nejsou žádné zastávky.")
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
                    .frame(maxWidth: .infinity)
                    .padding(32)
            }
        } else {
            TimelineView(.periodic(from: Date(), by: 30)) { context in
                VStack(spacing: 0) {
                    ForEach(vm.entries) { entry in
                        row(entry, now: context.date)
                    }
                }
            }
        }
    }

    private func row(_ entry: ODNearbyEntry, now: Date) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Button {
                onOpenStop(entry.stop)
            } label: {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(c.lineColor(for: entry.type == .train ? .train : .bus))
                        Image(systemName: entry.type.symbol)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Color.white)
                    }
                    .frame(width: 40, height: 40)
                    .accessibilityHidden(true)
                    Text(entry.stop.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.text)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Text(ODSearchTab.formatDistance(entry.distanceM))
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Zastávka \(entry.stop.name)") + ", " + ODSearchTab.formatDistance(entry.distanceM))
            .accessibilityHint("Zobrazí odjezdy ze zastávky")

            if entry.departures.isEmpty {
                Text("Nic v nejbližších hodinách nejede.")
                    .font(.footnote)
                    .foregroundStyle(c.muted)
                    .padding(.leading, 52)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(entry.departures) { d in
                            chip(d, now: now)
                        }
                    }
                    .padding(.leading, 52)
                    .padding(.trailing, 16)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .overlay(alignment: .bottom) { Rectangle().fill(c.line).frame(height: 1) }
    }

    /// Štítek linky + odpočet („6m“) do hodiny, jinak čas odjezdu.
    private func chip(_ d: ODDeparture, now: Date) -> some View {
        let mins = ODTime.minutesUntil(d.realTime, now: now)
        let when: String
        if mins <= 0 { when = L("teď") }
        else if mins < 60 { when = "\(mins)m" }
        else { when = ODTime.hm(d.realTime) }
        return HStack(spacing: 6) {
            ODLineBadge(line: d.line, cls: d.lineClass)
            Text(when)
                .font(mins < 60 ? .title3.weight(.bold) : .body)
                .monospacedDigit()
                .foregroundStyle(c.text)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Linka \(d.line.isEmpty ? L("bez čísla") : d.line)") + ", \(d.destination), " + (mins < 60 ? "za \(max(mins, 0)) min" : "v \(ODTime.hm(d.realTime))"))
    }
}
