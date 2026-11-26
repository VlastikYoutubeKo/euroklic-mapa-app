import Foundation
import SwiftUI

/// Novější stabilní vydání, než které je nainstalované.
struct AppUpdate: Equatable {
    let version: String
    let page: URL
}

/// „Je dostupná nová verze“. Appka se instaluje ze souboru (SideStore, IPA), takže se sama
/// neaktualizuje. Ptá se `api_app_version.php` (poslední stabilní GitHub Release) nejvýš jednou za
/// 12 hodin; obyčejný anonymní GET bez účtu a bez identifikátoru zařízení, viz PRIVACY.md.
/// Beta nikdy neobtěžuje.
enum UpdateChecker {
    private static let endpoint = URL(string: "https://euroklic.odjezdy.online/api_app_version.php")!
    static let pagePrefix = "https://euroklic.odjezdy.online/"
    private static let checkEvery: TimeInterval = 12 * 3600

    static let lastCheckKey = "update_last_check"
    static let latestVersionKey = "update_latest_version"
    static let latestPageKey = "update_latest_page"
    static let dismissedKey = "update_dismissed_version"

    /// `true`, když je `remote` (x.y.z) vyšší vydání než `local`; lokální beta (s příponou) nikdy.
    static func isNewer(_ remote: String, than local: String) -> Bool {
        if local.contains("-") { return false }
        guard let r = numbers(remote), let l = numbers(local) else { return false }
        for i in 0..<max(r.count, l.count) {
            let a = i < r.count ? r[i] : 0
            let b = i < l.count ? l[i] : 0
            if a != b { return a > b }
        }
        return false
    }

    static func numbers(_ version: String) -> [Int]? {
        var core = version.trimmingCharacters(in: .whitespaces)
        if core.hasPrefix("v") { core.removeFirst() }
        core = String(core.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false).first ?? "")
        let parts = core.split(separator: ".", omittingEmptySubsequences: false).map { Int($0) }
        guard !parts.isEmpty, !parts.contains(where: { $0 == nil }) else { return nil }
        return parts.compactMap { $0 }
    }

    private struct Remote: Decodable {
        let version: String?
        let page: String?
    }

    /// Zeptá se serveru, pokud poslední kontrola není novější než 12 hodin. Chyby se ignorují.
    static func refreshIfDue(defaults: UserDefaults = .standard, now: Date = Date()) async {
        let last = defaults.double(forKey: lastCheckKey)
        if last > 0, now.timeIntervalSince1970 - last < checkEvery { return }
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        config.httpCookieStorage = nil
        guard let (data, response) = try? await URLSession(configuration: config).data(from: endpoint),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let remote = try? JSONDecoder().decode(Remote.self, from: data),
              let version = remote.version, numbers(version) != nil,
              let page = remote.page, page.hasPrefix(pagePrefix)   // cizí odkaz se nikdy nestane tlačítkem
        else { return }
        defaults.set(now.timeIntervalSince1970, forKey: lastCheckKey)
        defaults.set(version, forKey: latestVersionKey)
        defaults.set(page, forKey: latestPageKey)
    }
}

@MainActor
final class UpdateStore: ObservableObject {
    @Published private(set) var available: AppUpdate?

    init() { publish() }

    func refreshIfDue() async {
        await UpdateChecker.refreshIfDue()
        publish()
    }

    func dismiss() {
        guard let update = available else { return }
        UserDefaults.standard.set(update.version, forKey: UpdateChecker.dismissedKey)
        publish()
    }

    private func publish() {
        let d = UserDefaults.standard
        guard let version = d.string(forKey: UpdateChecker.latestVersionKey),
              let page = d.string(forKey: UpdateChecker.latestPageKey).flatMap(URL.init(string:)),
              version != d.string(forKey: UpdateChecker.dismissedKey),
              UpdateChecker.isNewer(version, than: Changelog.currentVersion)
        else { available = nil; return }
        available = AppUpdate(version: version, page: page)
    }
}

/// Karta nahoře ve Více (obě záložky): jen když je dostupná novější verze.
struct UpdateSection: View {
    @EnvironmentObject private var store: UpdateStore

    var body: some View {
        if let update = store.available {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L("Je dostupná nová verze \(update.version)"))
                        .font(.headline)
                    Text("Nainstalujete ji ze stránky aplikace, stejně jako minule.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    HStack(spacing: 12) {
                        Link(destination: update.page) {
                            Text("Stáhnout")
                                .font(.body.weight(.semibold))
                                .frame(minHeight: 44)
                                .padding(.horizontal, 16)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Theme.brand)
                        Button("Později") { store.dismiss() }
                            .buttonStyle(.bordered)
                            .frame(minHeight: 44)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }
}
