import CoreLocation
import SwiftUI

/// Záložka Hledání: zastávky (diakritika nevadí), vozidla podle ev. č./SPZ, zastávky v okolí.
struct ODSearchTab: View {
    @Environment(\.odColors) private var c
    @StateObject private var vm = ODSearchViewModel()
    @ObservedObject var location: ODLocationProvider
    @State private var sheetStop: ODStop?

    let onShowVehicle: (ODVehicle) -> Void
    let onShowStopOnMap: (ODStop) -> Void

    private var nearbyKey: String {
        guard let l = location.location else { return "none" }
        // Zaokrouhleno (~500 m), ať se nenačítá při každém drobném posunu.
        return String(format: "%.3f,%.3f", l.coordinate.latitude, l.coordinate.longitude)
    }

    var body: some View {
        NavigationStack {
            List {
                if vm.trimmedQuery.count < 2 {
                    nearbySection
                } else {
                    resultsSections
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(c.bg)
            .navigationTitle("Hledání")
            .navigationBarTitleDisplayMode(.large)
            .searchable(
                text: $vm.query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Zastávka, ev. č. nebo SPZ"
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .onChange(of: vm.query) { _, _ in vm.queryChanged() }
            .task(id: nearbyKey) {
                if let l = location.location { await vm.loadNearby(around: l) }
            }
        }
        .sheet(item: $sheetStop) { stop in
            ODStopSheet(stop: stop, onShowOnMap: { s in
                sheetStop = nil
                onShowStopOnMap(s)
            })
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    // MARK: Výsledky

    @ViewBuilder
    private var resultsSections: some View {
        if let err = vm.errorText {
            ODBanner(symbol: "wifi.exclamationmark", text: err, tone: .warning)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
        }
        if vm.isSearching && vm.stops.isEmpty && vm.vehicles.isEmpty {
            HStack {
                Spacer()
                ProgressView("Hledám…").foregroundStyle(c.muted)
                Spacer()
            }
            .listRowBackground(Color.clear)
        }
        if !vm.stops.isEmpty {
            Section {
                ForEach(vm.stops) { stop in
                    stopRow(stop, distance: nil)
                }
            } header: {
                sectionHeader("Zastávky")
            }
        }
        if !vm.vehicles.isEmpty {
            Section {
                ForEach(vm.vehicles) { v in
                    vehicleRow(v)
                }
            } header: {
                sectionHeader("Vozidla")
            }
        }
        if vm.hasSearched && !vm.isSearching && vm.stops.isEmpty && vm.vehicles.isEmpty && vm.errorText == nil {
            ODEmptyState(
                symbol: "magnifyingglass",
                title: "Nic jsme nenašli",
                message: "Zkuste jiný název zastávky, evidenční číslo nebo SPZ vozidla."
            )
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder
    private var nearbySection: some View {
        if !vm.nearby.isEmpty {
            Section {
                ForEach(vm.nearby) { n in
                    stopRow(n.stop, distance: n.distanceM)
                }
            } header: {
                sectionHeader("V okolí")
            }
        } else if location.isAuthorized {
            ODEmptyState(
                symbol: "location.magnifyingglass",
                title: "Hledejte zastávku",
                message: "Začněte psát název zastávky, evidenční číslo nebo SPZ vozidla."
            )
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        } else {
            VStack(spacing: 12) {
                ODEmptyState(
                    symbol: "magnifyingglass",
                    title: "Hledejte zastávku",
                    message: "Začněte psát název zastávky, evidenční číslo nebo SPZ vozidla. Zastávky v okolí ukážeme, když povolíte polohu."
                )
                if !location.isDenied {
                    Button {
                        location.request()
                    } label: {
                        Label("Zobrazit zastávky v okolí", systemImage: "location")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(c.inkText)
                            .padding(.horizontal, 16)
                            .frame(minHeight: 44)
                            .background(c.ink, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity)
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.footnote.weight(.bold))
            .foregroundStyle(c.muted)
            .accessibilityAddTraits(.isHeader)
    }

    // MARK: Řádky

    private func stopRow(_ stop: ODStop, distance: Int?) -> some View {
        Button {
            sheetStop = stop
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "mappin.circle.fill")
                    .font(.title2)
                    .foregroundStyle(c.sign)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(stop.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.text)
                        .multilineTextAlignment(.leading)
                    let sub = [ODProvider.label(stop.provider), distance.map { Self.formatDistance($0) }]
                        .compactMap { $0 }
                        .filter { !$0.isEmpty }
                        .joined(separator: " · ")
                    if !sub.isEmpty {
                        Text(sub)
                            .font(.footnote)
                            .foregroundStyle(c.muted)
                    }
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(c.muted)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .listRowBackground(c.surface)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(stopLabel(stop, distance: distance))
        .accessibilityHint("Zobrazí odjezdy ze zastávky")
        .accessibilityAddTraits(.isButton)
    }

    private func stopLabel(_ stop: ODStop, distance: Int?) -> String {
        var s = "Zastávka \(stop.name)"
        if let distance { s += ", \(Self.formatDistance(distance))" }
        return s
    }

    private func vehicleRow(_ v: ODVehicle) -> some View {
        Button {
            onShowVehicle(v)
        } label: {
            HStack(spacing: 12) {
                ODLineBadge(line: v.badgeText, cls: v.lineClass)
                VStack(alignment: .leading, spacing: 2) {
                    Text(v.destination ?? v.type.label)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(c.text)
                        .multilineTextAlignment(.leading)
                    let sub = [v.evc.map { "ev. č. \($0)" }, v.spz, Optional(ODProvider.label(v.provider))]
                        .compactMap { $0 }
                        .filter { !$0.isEmpty }
                        .joined(separator: " · ")
                    Text(sub)
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                }
                Spacer(minLength: 0)
                Image(systemName: "map")
                    .foregroundStyle(c.muted)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .listRowBackground(c.surface)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(v.accessibilityText)
        .accessibilityHint("Ukáže vozidlo na mapě")
        .accessibilityAddTraits(.isButton)
    }

    static func formatDistance(_ m: Int) -> String {
        if m < 1000 { return "\(m) m" }
        let km = Double(m) / 1000
        return String(format: "%.1f km", km).replacingOccurrences(of: ".", with: ",")
    }
}
