import SwiftUI

/// Více tab — login card, live stats, disclaimers, links.
struct MoreView: View {
    @EnvironmentObject private var auth: AuthSession
    @EnvironmentObject private var places: PlacesStore
    @AppStorage(MapProvider.storageKey) private var mapProvider: MapProvider = .mapy

    var body: some View {
        NavigationStack {
            List {
                Section {
                    AuthCard()
                }

                Section("Statistiky") {
                    let s = places.stats
                    LabeledContent("Bezbariérových WC", value: placesCount(s.totalWc))
                    LabeledContent("Ověřeno hlasováním", value: "\(s.verifiedWc)")
                    LabeledContent("Nahlášeno nefunkční", value: "\(s.reportedWc)")
                    LabeledContent("Výdejních míst klíče", value: "\(s.totalPickup)")
                    if let top = s.topLiked {
                        LabeledContent("Nejlépe hodnocené", value: "\(top.name) (+\(top.likes)👍)")
                    }
                    if let worst = s.topDisliked {
                        LabeledContent("Nejvíce problémové", value: "\(worst.name) (\(worst.dislikes)👎)")
                    }
                }

                Section {
                    Picker("Podkladová mapa", selection: $mapProvider) {
                        ForEach(MapProvider.allCases) { Text($0.label).tag($0) }
                    }
                } header: {
                    Text("Mapa")
                } footer: {
                    Text("Mapy.com jsou podrobnější pro ČR a SR. Apple Maps fungují i bez klíče a při slabém připojení.")
                }

                Section("Přispět") {
                    NavigationLink {
                        AddPlaceView()
                    } label: {
                        Label("Přidat místo", systemImage: "plus.circle")
                    }
                }

                Section("Odkazy") {
                    Link(destination: URL(string: "https://euroklic.odjezdy.online")!) {
                        Label("euroklic.odjezdy.online", systemImage: "safari")
                    }
                    Link(destination: URL(string: "https://euroklic.odjezdy.online/clanky/jak-vybavit-euroklic/")!) {
                        Label("Jak získat Euroklíč", systemImage: "key")
                    }
                }

                Section {
                    NavigationLink {
                        ChangelogView(entries: Changelog.load())
                            .navigationTitle("Co je nového")
                            .navigationBarTitleDisplayMode(.inline)
                    } label: {
                        Label("Co je nového", systemImage: "sparkles")
                    }
                } footer: {
                    Text("Verze \(Changelog.currentVersion)")
                }

                Section("O projektu") {
                    Text("Nezávislý projekt. Data agregujeme z veřejných zdrojů. Nejsme oficiální aplikace systému Euroklíč ani NRZP ČR.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("Euroklíč je univerzální klíč k bezbariérovým toaletám, výtahům a schodišťovým plošinám po celé ČR. Vydává ho NRZP ČR.")
                        .font(.footnote)
                    Text("Pozor: NRZP ČR od července 2026 dočasně pozastavila výdej nových klíčů kvůli chybějícímu financování. Aktuální stav ověřujte na webu.")
                        .font(.footnote)
                        .foregroundStyle(Theme.accent)
                    LabeledContent("Verze", value: appVersion)
                }
            }
            .navigationTitle("Více")
        }
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }
}

struct AuthCard: View {
    @EnvironmentObject private var auth: AuthSession

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: auth.isLoggedIn ? "person.crop.circle.fill" : "person.crop.circle")
                .font(.system(size: 40))
                .foregroundStyle(auth.isLoggedIn ? Theme.tint : .secondary)

            VStack(alignment: .leading, spacing: 3) {
                Text(auth.isLoggedIn ? (auth.username ?? "Přihlášen") : "Nepřihlášen")
                    .font(.headline)
                Text(auth.isLoggedIn ? "Můžete přidávat místa." : "Přihlaste se pro přidávání míst.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()

            if auth.loading {
                ProgressView()
            } else if auth.isLoggedIn {
                Button("Odhlásit", role: .destructive) {
                    Task { await auth.logout() }
                }
                .font(.subheadline)
            } else {
                Button("Přihlásit se") {
                    auth.login()
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(.vertical, 4)
    }
}
