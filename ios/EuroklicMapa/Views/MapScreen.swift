import CoreLocation
import SwiftUI

/// Map tab: floating search replaced by the category chips + the ONE primary
/// action (Nejbližší WC), selected-place card pinned to the bottom — the same
/// persistent-sheet feel as the Android `BottomSheetScaffold`.
struct MapScreen: View {
    @EnvironmentObject private var places: PlacesStore
    @EnvironmentObject private var location: LocationService
    @EnvironmentObject private var favorites: FavoritesStore

    @State private var category: PlacesStore.Category = .all
    @State private var selected: PlaceItem?
    @State private var refreshing = false
    @State private var showAddPlace = false
    @State private var nearestMessage: String?

    var body: some View {
        NavigationStack {
            ZStack {
                MapView(
                    items: places.flattened(category: category, userLocation: location.lastLocation),
                    selectedKey: selected?.favoriteKey,
                    userLocation: location.lastLocation,
                    onSelect: { selected = $0 },
                )
                .ignoresSafeArea(edges: .bottom)

                VStack {
                    categoryChips
                    Spacer()
                    if let selected {
                        SelectedPlaceCard(
                            place: selected,
                            userLocation: location.lastLocation,
                            onClose: { self.selected = nil },
                        )
                        .padding(.horizontal)
                        .padding(.bottom, 8)
                    }
                }

                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        VStack(spacing: 14) {
                            // The app's one question, one tap away — same as the Android FAB.
                            Button(action: findNearest) {
                                Image(systemName: "location.fill")
                                    .font(.title2)
                                    .foregroundStyle(.white)
                                    .frame(width: 56, height: 56)
                                    .background(Circle().fill(Theme.brand))
                                    .shadow(radius: 4, y: 2)
                            }
                            .accessibilityLabel("Najít nejbližší WC")

                            Button(action: { showAddPlace = true }) {
                                Image(systemName: "plus")
                                    .font(.title3)
                                    .foregroundStyle(Theme.brand)
                                    .frame(width: 44, height: 44)
                                    .background(Circle().fill(.thinMaterial))
                                    .shadow(radius: 3, y: 1)
                            }
                            .accessibilityLabel("Přidat místo")
                        }
                        .padding(.trailing, 16)
                        .padding(.bottom, selected == nil ? 8 : 240)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if refreshing { ProgressView() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showAddPlace = true
                    } label: {
                        Image(systemName: "plus.circle")
                    }
                    .accessibilityLabel("Přidat místo")
                }
            }
            .sheet(isPresented: $showAddPlace) {
                AddPlaceView()
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

    private var categoryChips: some View {
        HStack(spacing: 6) {
            ForEach(PlacesStore.Category.allCases) { c in
                Button {
                    category = c
                } label: {
                    HStack(spacing: 4) {
                        if category == c { Image(systemName: "checkmark").font(.caption2) }
                        Text(c.label)
                    }
                    .font(.footnote.weight(category == c ? .semibold : .regular))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(
                        Capsule().fill(category == c ? Theme.brand : .thinMaterial),
                    )
                    .foregroundStyle(category == c ? .white : .primary)
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
        .padding(.horizontal)
    }

    private func findNearest() {
        location.requestAndRefresh()
        Task {
            if location.authorizationDenied {
                nearestMessage = "Poloha není známá. Povolte polohu v Nastavení a zkuste to znovu."
                return
            }
            // Wait briefly for a fresh fix when we don't have one yet.
            if location.lastLocation == nil {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
            }
            refreshing = true
            await places.refreshAll(near: location.nearParameter)
            refreshing = false
            guard let loc = location.lastLocation, let nearest = places.nearest(to: loc, category: category) else {
                nearestMessage = "Zatím nejsou stažená žádná místa."
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
                        .background(Capsule().fill(place.status == .reported ? Theme.accent.opacity(0.15) : Theme.success.opacity(0.15)))
                        .foregroundStyle(place.status == .reported ? Theme.accent : Theme.success)
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
