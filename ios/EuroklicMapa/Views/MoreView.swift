import SwiftUI

/// Více tab — login card, live stats, disclaimers, links.
struct MoreView: View {
    @EnvironmentObject private var auth: AuthSession
    @EnvironmentObject private var places: PlacesStore
    @AppStorage(MapProvider.storageKey) private var mapProvider: MapProvider = .mapy
    @AppStorage(Analytics.enabledKey) private var analyticsOn = false
    @State private var showIntro = false

    var body: some View {
        NavigationStack {
            List {
                ModePickerSection()

                Section {
                    AuthCard()
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
                    if auth.isAdmin {
                        NavigationLink {
                            AdminQueueView()
                        } label: {
                            Label("Ke schválení", systemImage: "checkmark.seal")
                        }
                    }
                }

                Section {
                    Toggle("Pomoci vylepšit aplikaci anonymním měřením", isOn: $analyticsOn)
                    Link(destination: URL(string: "https://euroklic.odjezdy.online/soukromi.php")!) {
                        Label("Zásady ochrany osobních údajů", systemImage: "hand.raised")
                    }
                    Link(destination: URL(string: "https://euroklic.odjezdy.online/podminky")!) {
                        Label("Podmínky použití", systemImage: "doc.text")
                    }
                } header: {
                    Text("Soukromí")
                } footer: {
                    Text("Dobrovolné, ve výchozím stavu vypnuté. Posílá jen anonymní počty použití funkcí (např. otevření místa), nikdy polohu, hledaný text ani údaje o tobě.")
                }

                DataTransferSection()

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
                        StatsListView()
                    } label: {
                        Label("Statistiky", systemImage: "chart.bar")
                    }
                    Button { showIntro = true } label: {
                        Label("Jak aplikace funguje", systemImage: "questionmark.circle")
                    }
                    .foregroundStyle(.primary)
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
                        .foregroundStyle(Theme.warningText)
                    LabeledContent("Verze", value: appVersion)
                }
            }
            .navigationTitle("Více")
            .sheet(isPresented: $showIntro) {
                OnboardingView(isRevisit: true) { showIntro = false }
            }
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
            avatar

            VStack(alignment: .leading, spacing: 3) {
                Text(auth.isLoggedIn ? (auth.username ?? L("Přihlášen")) : L("Nepřihlášen"))
                    .font(.headline)
                Text(auth.isLoggedIn ? L("Můžete přidávat místa, fotky a komentáře.") : L("Přihlaste se pro přidávání míst, fotek a komentářů."))
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

    /// Profilová fotka (Discord/Google) v kruhu; bez ní nebo při chybě SF Symbol.
    @ViewBuilder
    private var avatar: some View {
        if auth.isLoggedIn, let raw = auth.me?.avatar_url, !raw.isEmpty, let url = URL(string: raw) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .failure:
                    fallbackIcon
                default:
                    Color(.secondarySystemFill).overlay(ProgressView())
                }
            }
            .frame(width: 48, height: 48)
            .clipShape(Circle())
            .overlay(Circle().stroke(Color(.separator), lineWidth: 0.5))
            .accessibilityHidden(true)
        } else {
            fallbackIcon.accessibilityHidden(true)
        }
    }

    private var fallbackIcon: some View {
        Image(systemName: auth.isLoggedIn ? "person.crop.circle.fill" : "person.crop.circle")
            .font(.system(size: 44))
            .foregroundStyle(auth.isLoggedIn ? Theme.tint : .secondary)
            .frame(width: 48, height: 48)
    }
}

/// Statistiky seznamu míst (dřív přímo ve Více, teď o úroveň níž, ať praktická nastavení nejsou pod nimi).
struct StatsListView: View {
    @EnvironmentObject private var places: PlacesStore

    var body: some View {
        List {
            let s = places.stats
            LabeledContent("Bezbariérových WC", value: s.totalWc.formatted())
            LabeledContent("Ověřeno hlasováním", value: s.verifiedWc.formatted())
            LabeledContent("Nahlášeno nefunkční", value: s.reportedWc.formatted())
            LabeledContent("Výdejních míst klíče", value: s.totalPickup.formatted())
            if let top = s.topLiked {
                LabeledContent("Nejlépe hodnocené", value: "\(top.name) (+\(top.likes)👍)")
            }
            if let worst = s.topDisliked {
                LabeledContent("Nejvíce problémové", value: "\(worst.name) (\(worst.dislikes)👎)")
            }
        }
        .navigationTitle("Statistiky")
        .navigationBarTitleDisplayMode(.inline)
    }
}
