import SwiftUI

/// 4 tabs, same IA as the Android app: Mapa / Seznam / Oblíbené / Více.
struct RootTabView: View {
    var body: some View {
        TabView {
            MapScreen()
                .tabItem { Label("Mapa", systemImage: "map.fill") }
            PlacesListView()
                .tabItem { Label("Seznam", systemImage: "list.bullet") }
            FavoritesView()
                .tabItem { Label("Oblíbené", systemImage: "bookmark.fill") }
            MoreView()
                .tabItem { Label("Více", systemImage: "ellipsis") }
        }
    }
}
