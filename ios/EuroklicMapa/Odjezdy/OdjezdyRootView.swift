import SwiftUI

/// Vstupní bod režimu "Odjezdy" (veřejná doprava): spodní lišta Mapa / Hledání / Oblíbené / Více.
///
/// Napojení: `OdjezdyRootView()` stačí vložit tam, kde přepínač režimů zobrazuje druhý režim.
/// Nepotřebuje žádné závislosti ani environment z hlavní appky; barvy sleduje podle systému
/// (světlý/tmavý) a zvolené palety (`@AppStorage("od_palette")`).
struct OdjezdyRootView: View {
    enum Tab: Hashable {
        case map, search, favorites, more
    }

    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage(ODTheme.paletteKey) private var paletteId: String = ODPalette.defaultId

    @State private var tab: Tab = .map
    @StateObject private var map = ODMapViewModel()
    @StateObject private var favorites = ODFavoritesStore()
    @StateObject private var location = ODLocationProvider()

    var body: some View {
        let colors = ODColors.make(paletteId: paletteId, isDark: scheme == .dark)
        TabView(selection: $tab) {
            ODMapTab(vm: map, location: location, onOpenSearch: { tab = .search })
                .tabItem { Label("Mapa", systemImage: "map") }
                .tag(Tab.map)

            ODSearchTab(
                location: location,
                onShowVehicle: { vehicle in
                    map.focus(vehicle: vehicle)
                    tab = .map
                },
                onShowStopOnMap: { stop in
                    map.center(on: stop.coordinate, meters: 900)
                    tab = .map
                }
            )
            .tabItem { Label("Hledání", systemImage: "magnifyingglass") }
            .tag(Tab.search)

            ODFavoritesTab(onShowStopOnMap: { stop in
                map.center(on: stop.coordinate, meters: 900)
                tab = .map
            })
            .tabItem { Label("Oblíbené", systemImage: "bookmark.fill") }
            .tag(Tab.favorites)

            ODMoreTab()
                .tabItem { Label("Více", systemImage: "ellipsis") }
                .tag(Tab.more)
        }
        .tint(colors.accent)
        .background(colors.bg.ignoresSafeArea())
        .environment(\.odColors, colors)
        .environmentObject(favorites)
        .onAppear { map.setSceneActive(scenePhase == .active) }
        .onChange(of: scenePhase) { _, phase in
            map.setSceneActive(phase == .active)
        }
    }
}
