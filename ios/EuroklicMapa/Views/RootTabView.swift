import SwiftUI

/// 4 tabs, same IA as the Android app: Mapa / Seznam / Oblíbené / Více.
struct RootTabView: View {
    @AppStorage("last_seen_version") private var lastSeenVersion = ""
    @State private var whatsNew: ChangelogEntry?

    @AppStorage(AppMode.storageKey) private var mode: AppMode = .euroklic

    var body: some View {
        Group {
            if mode == .odjezdy {
                OdjezdyRootView()
                    .transition(.opacity)
            } else {
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
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: mode)
        .sheet(item: $whatsNew) { WhatsNewSheet(entry: $0) }
        .onAppear(perform: checkWhatsNew)
    }

    /// First launch ever: just remember the version. After an update: show that release's notes once.
    private func checkWhatsNew() {
        let current = Changelog.currentVersion
        guard !current.isEmpty, lastSeenVersion != current else { return }
        defer { lastSeenVersion = current }
        guard !lastSeenVersion.isEmpty else { return }
        whatsNew = Changelog.entry(for: current, in: Changelog.load())
    }
}
