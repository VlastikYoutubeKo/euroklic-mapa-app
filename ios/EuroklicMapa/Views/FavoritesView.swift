import SwiftUI

struct FavoritesView: View {
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var location: LocationService

    var body: some View {
        NavigationStack {
            Group {
                if favorites.items.isEmpty {
                    VStack(spacing: 10) {
                        Image(systemName: "bookmark").font(.largeTitle).foregroundStyle(.secondary)
                        Text("Zatím nic v oblíbených.")
                        Text("V detailu místa klepni na srdíčko / záložku.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    List {
                        ForEach(favorites.items) { place in
                            NavigationLink(value: place) {
                                PlaceRow(place: place, userLocation: location.lastLocation)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Oblíbené")
            .navigationDestination(for: PlaceItem.self) { place in
                DetailView(place: place)
            }
        }
    }
}
