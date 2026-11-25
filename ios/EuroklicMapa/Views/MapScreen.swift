import CoreLocation
import SwiftUI

/// Map tab: floating search replaced by the category chips + the ONE primary
/// action (Nejbližší WC), selected-place card pinned to the bottom — the same
/// persistent-sheet feel as the Android `BottomSheetScaffold`.
struct MapScreen: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @EnvironmentObject private var places: PlacesStore
    @EnvironmentObject private var location: LocationService
    @EnvironmentObject private var favorites: FavoritesStore

    @State private var category: PlacesStore.Category = .all
    @State private var selected: PlaceItem?
    @State private var refreshing = false
    @State private var showAddPlace = false
    @State private var nearestMessage: String?
    @State private var searchText = ""
    @State private var searchResults: [GeoResult] = []
    @State private var searching = false
    @State private var recenterTarget: CLLocationCoordinate2D?
    @FocusState private var searchFocused: Bool
    @AppStorage(MapProvider.storageKey) private var mapProvider: MapProvider = .mapy
    @ObservedObject private var network = NetworkMonitor.shared
    @StateObject private var viewport = MapViewport()
    @State private var downloading = false
    @State private var downloadProgress = 0.0
    @State private var toast: String?

    var body: some View {
        NavigationStack {
            ZStack {
                MapView(
                    items: places.flattened(category: category, userLocation: location.lastLocation),
                    selectedKey: selected?.favoriteKey,
                    userLocation: location.lastLocation,
                    provider: mapProvider,
                    recenterTarget: recenterTarget,
                    viewport: viewport,
                    onSelect: { selected = $0 },
                )
                .ignoresSafeArea()

                VStack {
                    topControls
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    Spacer()
                    if selected == nil {
                        VStack(alignment: .trailing, spacing: 12) {
                            mapActions
                            if let loc = location.lastLocation {
                                NearbyPanel(
                                    items: Array(places.flattened(category: category, userLocation: loc).prefix(dynamicTypeSize.isAccessibilitySize ? 1 : 3)),
                                    userLocation: loc,
                                    onSelect: { selected = $0 },
                                )
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 8)
                    }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $selected) { place in
                DetailView(place: place, asSheet: true)
                    .id(place.favoriteKey)
                    .presentationDetents([.fraction(0.42), .large])
                    .presentationBackgroundInteraction(.enabled(upThrough: .fraction(0.42)))
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(28)
            }
            .sheet(isPresented: $showAddPlace) {
                AddPlaceView()
            }
            .alert("Offline mapa", isPresented: .init(
                get: { toast != nil },
                set: { if !$0 { toast = nil } },
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(toast ?? "")
            }
            .alert("Nejbližší WC", isPresented: .init(
                get: { nearestMessage != nil },
                set: { if !$0 { nearestMessage = nil } },
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(nearestMessage ?? "")
            }
            .task {
                if places.wc.isEmpty { await refresh() }
            }
        }
    }

    private var mapActions: some View {
                        VStack(alignment: .trailing, spacing: 12) {
                            // Secondary actions live in one menu so the map stays calm.
                            Menu {
                                Button { showAddPlace = true } label: { Label("Přidat místo", systemImage: "plus") }
                                Button(action: downloadMap) { Label("Stáhnout mapu pro offline použití", systemImage: "arrow.down.circle") }
                                    .disabled(downloading)
                            } label: {
                                Group {
                                    if downloading {
                                        ProgressView(value: downloadProgress).progressViewStyle(.circular)
                                    } else {
                                        Image(systemName: "ellipsis").font(.title3.weight(.semibold)).foregroundStyle(Theme.tint)
                                    }
                                }
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(.regularMaterial))
                                .overlay(Circle().stroke(Color(.separator), lineWidth: 0.5))
                            }
                            .accessibilityLabel("Další akce")

                            // The app's one question, one tap away.
                            Button(action: findNearest) {
                                Label("Nejbližší WC", systemImage: "location.fill")
                                    .font(.headline)
                                    .foregroundStyle(.white)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 8)
                                    .frame(minHeight: 52)
                                    .background(Capsule().fill(Theme.brand))
                                    .shadow(radius: 4, y: 2)
                            }
                            .accessibilityLabel("Najít nejbližší WC")
                        }
    }

    /// Address search (Nominatim, CZ/SK) above the category chips; picking a hit flies the map there.
    private var topControls: some View {
        VStack(spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                    TextField("Hledat adresu", text: $searchText)
                        .focused($searchFocused)
                        .submitLabel(.search)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit { runSearch() }
                    if searching { ProgressView() }
                    if !searchText.isEmpty {
                        Button {
                            searchText = ""
                            searchResults = []
                        } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                        }
                        .accessibilityLabel("Smazat hledání")
                    }
                }
                .padding(.horizontal, 14)
                .frame(minHeight: 44)

                ForEach(searchResults) { hit in
                    Divider()
                    Button {
                        recenterTarget = hit.coordinate
                        searchResults = []
                        searchFocused = false
                    } label: {
                        Text(hit.name)
                            .font(.subheadline)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(RoundedRectangle(cornerRadius: 22).fill(.regularMaterial))
            ModeSwitchPill(target: .odjezdy)
            }
            .padding(.horizontal)

            categoryChips
            if !network.isOnline {
                Label("Offline režim · místa a mapa ze stažených dat", systemImage: "wifi.slash")
                    .font(.footnote.weight(.medium))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(.regularMaterial))
            }
        }
    }

    /// Pre-fills the on-disk tile cache for the visible area (Mapy.com only).
    private func downloadMap() {
        guard !downloading else { return }
        guard network.isOnline else {
            toast = L("Jste offline. Mapu jde stáhnout jen s připojením.")
            return
        }
        let rect = viewport.rect
        guard !rect.isNull else { return }
        downloading = true
        downloadProgress = 0
        Task {
            let outcome = await OfflineTiles.prefetch(visible: rect) { downloadProgress = $0 }
            downloading = false
            switch outcome {
            case .done: toast = L("Mapa téhle oblasti je stažená pro offline použití.")
            case .tooLarge: toast = L("Vybraná oblast je na stažení moc velká. Přibližte mapu na menší oblast a zkuste to znovu.")
            case .failed: toast = L("Stahování mapy se nezdařilo. Zkuste to znovu s lepším připojením.")
            case .unavailable: toast = L("Offline mapa funguje s mapou Mapy.com (Více → Mapa).")
            }
        }
    }

    private func runSearch() {
        let q = searchText
        searching = true
        Task {
            let hits = await Geocoder.search(q)
            Analytics.track(.search)
            searchResults = hits
            searching = false
        }
    }

    private var categoryChips: some View {
        HStack(spacing: 6) {
            ForEach(PlacesStore.Category.allCases) { c in
                Button {
                    category = c
                } label: {
                    let isOn = category == c
                    let fill: AnyShapeStyle = isOn ? AnyShapeStyle(Theme.brand) : AnyShapeStyle(.thinMaterial)
                    let textColor: Color = isOn ? Color.white : Color.primary
                    HStack(spacing: 4) {
                        if isOn { Image(systemName: "checkmark").font(.caption2) }
                        Text(c.label)
                    }
                    .font(.footnote.weight(isOn ? .semibold : .regular))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Capsule().fill(fill))
                    .foregroundStyle(textColor)
                }
                .buttonStyle(.plain)
            }
            Spacer()
            if refreshing { ProgressView().padding(8).background(Circle().fill(.thinMaterial)) }
        }
        .padding(.horizontal)
    }

    private func findNearest() {
        location.requestAndRefresh()
        Task {
            if location.authorizationDenied {
                nearestMessage = L("Poloha není známá. Povolte polohu v Nastavení a zkuste to znovu.")
                return
            }
            // Wait briefly for a fresh fix when we don't have one yet.
            if location.lastLocation == nil {
                try? await Task.sleep(for: .seconds(1.5))
            }
            refreshing = true
            await places.refreshAll(near: location.nearParameter)
            refreshing = false
            guard let loc = location.lastLocation, let nearest = places.nearest(to: loc, category: category) else {
                nearestMessage = L("Zatím nejsou stažená žádná místa.")
                return
            }
            selected = nearest
        }
    }

    private func refresh() async {
        refreshing = true
        location.requestAndRefresh()
        await places.refreshAll(near: location.nearParameter)
        refreshing = false
    }
}

/// Bottom card over the map for the selected marker — Android's `SelectedPlaceCard`.
struct SelectedPlaceCard: View {
    @EnvironmentObject private var favorites: FavoritesStore
    let place: PlaceItem
    let userLocation: CLLocation?
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                Text(place.name)
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .accessibilityLabel("Zavřít")
            }

            HStack(spacing: 8) {
                Text(place.sourceLabel)
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(.quaternary.opacity(0.6)))
                if let badge = place.badgeLabel {
                    Text(badge)
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(place.badgeColor.opacity(0.15)))
                        .foregroundStyle(place.badgeColor)
                }
            }

            if let userLocation {
                let d = distanceTo(userLocation)
                Text("\(formatDistance(d)) · \(formatWalkingTime(d)) pěšky (vzdušnou čarou)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            NavigationLink {
                DetailView(place: place)
            } label: {
                Text("Zobrazit podrobnosti")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderless)
            .font(.subheadline.weight(.semibold))

            Button {
                openNavigation()
            } label: {
                Label("Navigovat", systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 999).fill(Theme.brand))
                    .foregroundStyle(.white)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(.regularMaterial))
        .shadow(radius: 8, y: 3)
    }

    private func distanceTo(_ loc: CLLocation) -> CLLocationDistance {
        loc.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
    }

    private func openNavigation() {
        Analytics.track(.navigate)
        let c = place.coordinate
        let directions = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(c.latitude),\(c.longitude)")
        let geo = URL(string: "geo:\(c.latitude),\(c.longitude)")
        // Maps/Mapy/Waze handle the directions URL; bare geo is the fallback.
        if let directions, UIApplication.shared.canOpenURL(directions) {
            UIApplication.shared.open(directions)
        } else if let geo {
            UIApplication.shared.open(geo)
        }
    }
}

/// Compact "nearby" strip — the Android sheet's peek state, without the full list.
struct NearbyPanel: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let items: [PlaceItem]
    let userLocation: CLLocation
    let onSelect: (PlaceItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Nejbližší místa")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 14)
                .padding(.top, 12)
                .padding(.bottom, 4)
            ForEach(items) { item in
                Button { onSelect(item) } label: {
                    Group {
                        if dynamicTypeSize.isAccessibilitySize {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(alignment: .top, spacing: 10) {
                                    Image(systemName: item.isPickup ? "shippingbox.fill" : "figure.stand")
                                        .foregroundStyle(Theme.tint)
                                        .frame(width: 24)
                                    Text(item.name).multilineTextAlignment(.leading)
                                }
                                Text(formatDistance(distance(item)))
                                    .font(.subheadline.monospacedDigit())
                                    .foregroundStyle(.secondary)
                                    .padding(.leading, 34)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 8)
                        } else {
                            HStack(spacing: 10) {
                                Image(systemName: item.isPickup ? "shippingbox.fill" : "figure.stand")
                                    .foregroundStyle(Theme.tint)
                                    .frame(width: 24)
                                Text(item.name).lineLimit(2).multilineTextAlignment(.leading)
                                Spacer(minLength: 8)
                                Text(formatDistance(distance(item)))
                                    .font(.subheadline.monospacedDigit())
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .frame(minHeight: 52)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(item.name), \(formatDistance(distance(item)))")
            }
        }
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 20).fill(.regularMaterial))
        .shadow(radius: 6, y: 2)
    }

    private func distance(_ item: PlaceItem) -> CLLocationDistance {
        userLocation.distance(from: CLLocation(latitude: item.latitude, longitude: item.longitude))
    }
}
