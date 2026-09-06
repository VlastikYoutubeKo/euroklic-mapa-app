import SwiftUI

@main
struct EuroklicMapaApp: App {
    @StateObject private var places = PlacesStore()
    @StateObject private var location = LocationService()
    @StateObject private var favorites = FavoritesStore()
    @StateObject private var auth = AuthSession()

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .environmentObject(places)
                .environmentObject(location)
                .environmentObject(favorites)
                .environmentObject(auth)
                .tint(Theme.brand)
        }
    }
}
