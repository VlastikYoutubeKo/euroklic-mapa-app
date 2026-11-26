import SwiftUI

@main
struct EuroklicMapaApp: App {
    @StateObject private var places = PlacesStore()
    @StateObject private var location = LocationService()
    @StateObject private var favorites = FavoritesStore()
    @StateObject private var auth = AuthSession()
    @StateObject private var update = UpdateStore()
    @StateObject private var route = RouteStore()

    var body: some Scene {
        WindowGroup {
            AppEntryView()
                .environmentObject(places)
                .environmentObject(location)
                .environmentObject(favorites)
                .environmentObject(auth)
                .environmentObject(update)
                .environmentObject(route)
                .tint(Theme.tint)
                .onAppear { Analytics.track(.appOpen) }
                .task { await update.refreshIfDue() }
        }
    }
}
