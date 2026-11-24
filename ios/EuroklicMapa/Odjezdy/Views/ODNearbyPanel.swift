import CoreLocation
import SwiftUI

/// Zastávka v okolí s nejbližšími odjezdy (pro spodní panel na mapě).
struct ODNearbyEntry: Identifiable {
    let stop: ODStop
    let distanceM: Int
    let departures: [ODDeparture]
    /// Odjezdy se nepodařilo načíst (není to totéž jako „nic nejede“).
    let failed: Bool
    var id: String { stop.id }

    /// Převažující typ dopravy – podle něj je ikona v pruhu zastávky.
    var type: ODTransportType {
        var counts: [ODTransportType: Int] = [:]
        for d in departures { counts[d.type, default: 0] += 1 }
        return counts.max { $0.value < $1.value }?.key ?? .bus
    }
}

/// Zastávky kolem polohy a jejich nejbližší odjezdy; obnova po ~30 s, dokud je panel rozbalený.
@MainActor
final class ODNearbyViewModel: ObservableObject {
    @Published private(set) var entries: [ODNearbyEntry] = []
    @Published private(set) var isLoading = true
    /// Poslední načtení selhalo (zobrazená data můžou být starší).
    @Published private(set) var lastLoadFailed = false

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
            try Task.checkCancellation()
            var seen = Set<String>()
            let nearest = list
                .map { ODNearbyStop(stop: $0, distanceM: Int(location.distance(from: CLLocation(latitude: $0.lat, longitude: $0.lng)))) }
                .sorted { $0.distanceM < $1.distanceM }
                .filter { seen.insert($0.stop.name).inserted }
                .prefix(Self.maxStops)

            var out: [ODNearbyEntry] = []
            for n in nearest {
                try Task.checkCancellation()
                do {
                    let deps = try await api.departures(stopId: n.stop.id, limit: 12, minutes: 180)
                    // Časový filtr před prefixem, ať odjeté spoje nevytlačí platné (view filtruje ještě jednou).
                    let cutoff = Date().addingTimeInterval(-60)
                    let upcoming = deps
                        .filter { !$0.cancelled && $0.realTime >= cutoff }
                        .sorted { $0.realTime < $1.realTime }
                    out.append(ODNearbyEntry(stop: n.stop, distanceM: n.distanceM, departures: Array(upcoming.prefix(8)), failed: false))
                } catch is CancellationError {
                    throw CancellationError()
                } catch {
                    out.append(ODNearbyEntry(stop: n.stop, distanceM: n.distanceM, departures: [], failed: true))
                }
            }
            // Starý běh (poloha se mezitím změnila) nesmí přepsat nový výsledek.
            try Task.checkCancellation()
            entries = out
            lastLoadFailed = false
        } catch is CancellationError {
            return
        } catch {
            // Předchozí data zůstanou, jen se ukáže upozornění.
            lastLoadFailed = true
        }
        isLoading = false
    }
}

/// Spodní panel na mapě: pole pro hledání a zastávky v okolí s odjezdy (ve stylu cedule a řádků odjezdů z appky).
/// Tažením úchytu nahoru/dolů nebo klepnutím na něj se rozbalí a sbalí.
struct ODNearbyPanel: View {
    @Environment(\.odColors) private var c
    @ObservedObject var location: ODLocationProvider
    let expandedHeight: CGFloat
    /// Celá dostupná výška nad lištou; rozbalený panel ji nesmí přerůst.
    let availableHeight: CGFloat
    let onSearch: () -> Void
    let onOpenStop: (ODStop) -> Void

    @StateObject private var vm = ODNearbyViewModel()
    @State private var expanded = false

    /// Úchyt (44) + pole hledání (48+) + okraje; roste s velikostí písma.
    @ScaledMetric(relativeTo: .body) private var collapsedHeight: CGFloat = 116

    private var locationKey: String {
        guard let l = location.location else { return "none" }
        return String(format: "%.3f,%.3f", l.coordinate.latitude, l.coordinate.longitude)
    }

    /// Rozbalený panel nesmí být nižší než sbalený (nízký landscape, velké písmo).
    /// Shora omezený dostupnou výškou (úchyt i hledání musí zůstat na obrazovce).
    private var openHeight: CGFloat {
        min(max(expandedHeight, collapsedHeight + 180), max(availableHeight - 16, collapsedHeight))
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                handle
                searchField
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }
            // Tažení jen za horní část, ať nesoupeří s posouváním seznamu.
            .gesture(
                DragGesture(minimumDistance: 12).onEnded { value in
                    if value.translation.height < -30 { expanded = true }
                    if value.translation.height > 30 { expanded = false }
                }
            )
            if expanded {
                list
            }
        }
        .frame(height: expanded ? openHeight : collapsedHeight, alignment: .top)
        .frame(maxWidth: .infinity)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 10, topTrailingRadius: 10, style: .continuous)
                .fill(c.surface)
                .shadow(color: Color.black.opacity(0.25), radius: 8, y: -2)
                .ignoresSafeArea(edges: .bottom)
        )
        .animation(.easeInOut(duration: 0.22), value: expanded)
        // Odjezdy se načítají jen při rozbaleném panelu (sbalený je neukazuje).
        .task(id: "\(locationKey)|\(expanded)") {
            if expanded, let l = location.location { await vm.run(around: l) }
        }
    }

    private var handle: some View {
        Button {
            expanded.toggle()
        } label: {
            Capsule()
                .fill(c.muted.opacity(0.6))
                .frame(width: 40, height: 5)
                .frame(maxWidth: .infinity, minHeight: 44)
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
                Text("Zastávka, linka nebo SPZ")
                    .font(.body.weight(.semibold))
                Spacer(minLength: 0)
            }
            .foregroundStyle(c.text)
            .padding(.horizontal, 14)
            .frame(minHeight: 48)
            .background(c.surface2, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous).stroke(c.line, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Otevře hledání zastávky nebo vozidla")
    }

    // MARK: Seznam

    private var list: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(L("Zastávky v okolí").uppercased())
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(c.muted)
                    .padding(.horizontal, 16)
                    .padding(.top, 6)
                    .padding(.bottom, 6)
                    .accessibilityAddTraits(.isHeader)
                if vm.lastLoadFailed && !vm.entries.isEmpty {
                    ODBanner(symbol: "wifi.slash", text: L("Odjezdy se nepodařilo obnovit, ukazuji poslední načtené."), tone: .warning)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                }
                content
            }
            .padding(.bottom, 100) // poslední řádek se musí dát vysunout nad plovoucí lištu záložek
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
                Text(vm.lastLoadFailed ? L("Zastávky v okolí se nepodařilo načíst.") : L("V okolí nejsou žádné zastávky."))
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
                    .multilineTextAlignment(.center)
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
        // Filtr podle aktuálního času, ať po neúspěšné obnově nezůstanou odjeté spoje jako „teď“.
        let upcoming = entry.departures.filter { $0.realTime >= now.addingTimeInterval(-60) }
        return VStack(spacing: 0) {
            Button {
                onOpenStop(entry.stop)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: entry.type.symbol)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.signText)
                        .accessibilityHidden(true)
                    Text(entry.stop.name)
                        .font(.headline)
                        .foregroundStyle(c.signText)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Text(ODSearchTab.formatDistance(entry.distanceM))
                        .font(.footnote)
                        .foregroundStyle(c.signText)
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 44)
                .background(c.sign)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("Zastávka \(entry.stop.name)") + ", " + ODSearchTab.formatDistance(entry.distanceM))
            .accessibilityHint("Zobrazí odjezdy ze zastávky")

            if upcoming.isEmpty {
                Text(entry.failed ? L("Odjezdy se nepodařilo načíst.") : L("Nic v nejbližších hodinách nejede."))
                    .font(.footnote)
                    .foregroundStyle(c.muted)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ForEach(upcoming.prefix(3)) { d in
                    ODDepartureRow(departure: d, now: now, isExpanded: false, isInteractive: false)
                }
            }
        }
        .padding(.bottom, 14)
    }
}
