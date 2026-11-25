import SwiftUI

struct FavoritesView: View {
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var location: LocationService

    var body: some View {
        NavigationStack {
            Group {
                if favorites.items.isEmpty {
                    ContentUnavailableView {
                        Label("Zatím nic v oblíbených", systemImage: "bookmark")
                    } description: {
                        Text("V detailu místa klepněte na záložku, ať se sem místo uloží.")
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
