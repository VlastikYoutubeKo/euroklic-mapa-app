import MapKit
import SwiftUI

/// Záložka Mapa: živá vozidla jako štítky s linkou, filtry dopravy nahoře,
/// klepnutí na vozidlo = karta, klepnutí na zastávku = odjezdy.
struct ODMapTab: View {
    @Environment(\.odColors) private var c
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    @ObservedObject var vm: ODMapViewModel
    @ObservedObject var location: ODLocationProvider
    let onOpenSearch: () -> Void
    @AppStorage(MapProvider.storageKey) private var providerRaw: String = MapProvider.mapy.rawValue
    @AppStorage(ODTheme.paletteKey) private var paletteId: String = ODPalette.defaultId
    @State private var showLocationDenied = false

    private var provider: MapProvider { MapProvider(rawValue: providerRaw) ?? .mapy }

    private var signHex: UInt32 {
        let p = ODPalette.palette(id: paletteId)
        return scheme == .dark ? p.dark.sign : p.light.sign
    }

    private var routeHex: UInt32 {
        if let v = vm.selectedVehicle {
            return ODTheme.lineHex(v.lineClass, dark: scheme == .dark)
        }
        return 0x1d5fd1
    }

    var body: some View {
        ZStack(alignment: .top) {
            ODVehicleMapView(
                vehicles: vm.displayed,
                fetchedAt: vm.lastFetch,
                stops: vm.stops,
                selectedVehicleId: vm.selectedVehicleId,
                route: vm.route,
                recenter: vm.recenter,
                userLocation: location.location,
                showsUserLocation: location.isAuthorized,
                provider: provider,
                isDark: scheme == .dark,
                signColor: signHex,
                routeColor: routeHex,
                onRegion: { vm.regionChanged($0) },
                onSelectVehicle: { vm.selectVehicle($0) },
                onSelectStop: { vm.openStop = $0 },
                onTapEmpty: { vm.clearSelection() }
            )
            .ignoresSafeArea(edges: .top)

            VStack(spacing: 8) {
                HStack {
                    ModeSwitchPill(target: .euroklic) // one tap back to the toilet map
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                filterRow
                notices
                Spacer(minLength: 0)
                if let v = vm.selectedVehicle {
                    ODVehicleCard(vm: vm, vehicle: v)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 28)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .padding(.top, 8)
        }
        .overlay(alignment: .bottom) {
            if vm.selectedVehicle == nil {
                GeometryReader { geo in
                    ODNearbyPanel(
                        location: location,
                        expandedHeight: geo.size.height * 0.55,
                        availableHeight: geo.size.height,
                        onSearch: onOpenSearch,
                        onOpenStop: { vm.openStop = $0 }
                    )
                    .frame(maxHeight: .infinity, alignment: .bottom)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: vm.selectedVehicleId)
        .onAppear { vm.setTabVisible(true) }
        .onDisappear { vm.setTabVisible(false) }
        .sheet(item: $vm.openStop) { stop in
            ODStopSheet(stop: stop)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationBackgroundInteraction(.enabled(upThrough: .medium))
        }
        .sheet(isPresented: $vm.showVehicleDetail) {
            ODVehicleDetailSheet(vm: vm)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .alert("Poloha je vypnutá", isPresented: $showLocationDenied) {
            Button("Otevřít Nastavení") {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
            Button("Zrušit", role: .cancel) {}
        } message: {
            Text("Appka funguje i bez polohy. Chcete-li mapu vycentrovat na sebe, povolte polohu v Nastavení.")
        }
    }

    // MARK: Filtry

    private var filterRow: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(ODMapFilter.allCases) { f in
                ODRoundFilterButton(filter: f, isOn: vm.enabled.contains(f)) {
                    var set = vm.enabled
                    if set.contains(f) { set.remove(f) } else { set.insert(f) }
                    vm.enabled = set
                }
            }
            Spacer(minLength: 0)
            Button {
                if let loc = location.location {
                    vm.center(on: loc.coordinate, meters: 2000)
                } else if location.isDenied {
                    showLocationDenied = true
                } else {
                    location.request()
                }
            } label: {
                Image(systemName: "location.fill")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(c.accent)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(c.surface))
                    .overlay(Circle().stroke(c.line, lineWidth: 1.5))
                    .shadow(color: Color.black.opacity(0.18), radius: 4, y: 2)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Moje poloha")
            .accessibilityHint("Vycentruje mapu na vás")
        }
        .padding(.horizontal, 12)
    }

    // MARK: Hlášky

    @ViewBuilder
    private var notices: some View {
        VStack(spacing: 6) {
            switch vm.notice {
            case .offline:
                if let t = vm.lastFetch {
                    ODBanner(symbol: "wifi.slash", text: L("Jste offline. Vozidla ukazují stav z \(ODTime.hm(t))."), tone: .warning)
                } else {
                    ODBanner(symbol: "wifi.slash", text: L("Jste offline. Vozidla se nepodařilo načíst."), tone: .warning)
                }
            case .rateLimited:
                ODBanner(symbol: "hourglass", text: L("Server odjezdy.online je dočasně přetížený. Vozidla se obnoví za chvilku."), tone: .warning)
            case .error(let message):
                ODBanner(symbol: "exclamationmark.triangle", text: message, tone: .warning)
            case nil:
                EmptyView()
            }

            if vm.tooFar && vm.enabled.contains(where: { $0.isVehicleFilter }) {
                ODBanner(symbol: "plus.magnifyingglass", text: L("Přibližte mapu, aby se zobrazila vozidla."))
            } else if vm.truncated {
                ODBanner(symbol: "info.circle", text: L("Zobrazeno \(ODMapViewModel.maxMarkers) nejbližších vozidel. Přibližte mapu pro další."))
            }

            if vm.lastFetch == nil, vm.notice == nil, vm.enabled.contains(where: { $0.isVehicleFilter }) {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Načítám vozidla…")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(c.text)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(c.surface, in: Capsule())
                .overlay(Capsule().stroke(c.line, lineWidth: 1))
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.horizontal, 12)
    }
}
