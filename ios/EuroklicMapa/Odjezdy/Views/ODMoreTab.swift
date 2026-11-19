import SwiftUI

/// Záložka Více: Aktuality, barevné schéma, O aplikaci.
struct ODMoreTab: View {
    @Environment(\.odColors) private var c
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink {
                        ODAlertsView()
                    } label: {
                        rowLabel("Aktuality", symbol: "exclamationmark.triangle", detail: "Výluky a mimořádnosti")
                    }
                    NavigationLink {
                        ODPalettePickerView()
                    } label: {
                        rowLabel("Barevné schéma", symbol: "paintpalette", detail: "Vzhled režimu Odjezdy")
                    }
                    NavigationLink {
                        ODAboutView()
                    } label: {
                        rowLabel("O aplikaci", symbol: "info.circle", detail: "Zdroje dat a soukromí")
                    }
                }
                .listRowBackground(c.surface)

                Section {
                    Button {
                        if let url = URL(string: "https://app.odjezdy.online") { openURL(url) }
                    } label: {
                        rowLabel("Web Odjezdy Online", symbol: "safari", detail: "app.odjezdy.online")
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Otevře web v prohlížeči")
                }
                .listRowBackground(c.surface)
            }
            .scrollContentBackground(.hidden)
            .background(c.bg)
            .navigationTitle("Více")
        }
    }

    private func rowLabel(_ title: String, symbol: String, detail: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.title3)
                .foregroundStyle(c.accent)
                .frame(width: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(c.text)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(c.muted)
            }
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Barevné schéma

struct ODPalettePickerView: View {
    @Environment(\.odColors) private var c
    @Environment(\.colorScheme) private var scheme
    @AppStorage(ODTheme.paletteKey) private var paletteId: String = ODPalette.defaultId

    var body: some View {
        List {
            Section {
                ForEach(ODPalette.all) { p in
                    Button {
                        paletteId = p.id
                    } label: {
                        HStack(spacing: 12) {
                            swatches(p)
                            Text(p.name)
                                .font(.body)
                                .foregroundStyle(c.text)
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                            if paletteId == p.id {
                                Image(systemName: "checkmark")
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(c.accent)
                                    .accessibilityHidden(true)
                            }
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(p.name)
                    .accessibilityAddTraits(paletteId == p.id ? [.isButton, .isSelected] : .isButton)
                }
            } footer: {
                Text("Barvy stejné jako na webu. Světlý nebo tmavý režim se řídí nastavením telefonu.")
                    .foregroundStyle(c.muted)
            }
            .listRowBackground(c.surface)
        }
        .scrollContentBackground(.hidden)
        .background(c.bg)
        .navigationTitle("Barevné schéma")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func swatches(_ p: ODPalette) -> some View {
        let t = scheme == .dark ? p.dark : p.light
        let colors: [UInt32] = [t.sign, t.mark, t.surface, t.ink]
        return HStack(spacing: 0) {
            ForEach(0..<colors.count, id: \.self) { i in
                Rectangle().fill(Color(odHex: colors[i]))
            }
        }
        .frame(width: 64, height: 28)
        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 4, style: .continuous).stroke(c.line, lineWidth: 1))
        .accessibilityHidden(true)
    }
}

// MARK: - O aplikaci

struct ODAboutView: View {
    @Environment(\.odColors) private var c

    private var version: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? ""
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                block(
                    "Co to je",
                    "Režim Odjezdy ukazuje živou polohu autobusů, vlaků, tramvají a dalších vozidel a odjezdy ze zastávek v Česku. Funguje podobně jako web app.odjezdy.online."
                )
                block(
                    "Odkud jsou data",
                    "Data poskytuje odjezdy.online ze zdrojů jednotlivých dopravců a integrovaných systémů (například PID, DÚK, České dráhy a Správa železnic). Poloha a zpoždění vozidel se obnovují po desítkách sekund a mohou být nepřesné. Pro rozhodující spoje se spoléhejte i na informace na zastávce."
                )
                block(
                    "Soukromí",
                    "Poloha zůstává v telefonu. Při zobrazení zastávek v okolí se na server odjezdy.online posílá jen přibližný čtverec kolem vás. Oblíbené zastávky se ukládají pouze lokálně a nepřihlašujete se nikam."
                )
                block(
                    "Mapové podklady",
                    "Mapa používá podklad Mapy.com (© Seznam.cz a.s. a další) nebo Apple Maps, podle nastavení v aplikaci."
                )
                if !version.isEmpty {
                    Text("Verze aplikace \(version)")
                        .font(.footnote)
                        .foregroundStyle(c.muted)
                }
            }
            .padding(16)
        }
        .background(c.bg)
        .navigationTitle("O aplikaci")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func block(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
                .foregroundStyle(c.text)
                .accessibilityAddTraits(.isHeader)
            Text(text)
                .font(.body)
                .foregroundStyle(c.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .odCard()
    }
}

// MARK: - Aktuality

@MainActor
final class ODAlertsViewModel: ObservableObject {
    @Published private(set) var alerts: [ODAlert] = []
    @Published private(set) var isLoading = true
    @Published private(set) var errorText: String?
    @Published var onlyActive = true
    @Published var provider: String?
    @Published var text = ""

    private let api = ODAPIClient.shared

    nonisolated init() {}

    func load() async {
        do {
            let list = try await api.alerts()
            alerts = list.sorted { ($0.validFrom ?? .distantPast) > ($1.validFrom ?? .distantPast) }
            errorText = nil
        } catch is CancellationError {
            return
        } catch let error as ODAPIError {
            errorText = error.errorDescription
        } catch {
            errorText = "Aktuality se nepodařilo načíst."
        }
        isLoading = false
    }

    var providers: [String] {
        Array(Set(alerts.map { $0.provider }.filter { !$0.isEmpty })).sorted()
    }

    var filtered: [ODAlert] {
        let now = Date()
        let q = text.trimmingCharacters(in: .whitespaces)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "cs_CZ"))
        return alerts.filter { a in
            if onlyActive && !a.isActive(at: now) { return false }
            if let p = provider, a.provider != p { return false }
            if q.isEmpty { return true }
            let hay = ([a.title, a.summary ?? ""] + a.lines + a.stops)
                .joined(separator: " ")
                .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "cs_CZ"))
            return hay.contains(q)
        }
    }
}

enum ODPlainText {
    /// Hrubě odstraní HTML značky a entity (popisky mimořádností jsou volný text).
    static func strip(_ raw: String) -> String {
        var s = raw.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
        s = s.replacingOccurrences(of: "</p>", with: "\n")
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

struct ODAlertsView: View {
    @Environment(\.odColors) private var c
    @StateObject private var vm = ODAlertsViewModel()

    var body: some View {
        List {
            Section {
                Toggle("Jen platné", isOn: $vm.onlyActive)
                    .font(.body)
                    .foregroundStyle(c.text)
                    .frame(minHeight: 44)
                if vm.providers.count >= 2 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 2) {
                            ODChip(title: "Vše", isOn: vm.provider == nil) { vm.provider = nil }
                            ForEach(vm.providers, id: \.self) { p in
                                ODChip(title: ODProvider.label(p), isOn: vm.provider == p) { vm.provider = p }
                            }
                        }
                    }
                }
            }
            .listRowBackground(c.surface)

            if let err = vm.errorText {
                ODBanner(symbol: "wifi.exclamationmark", text: err, tone: .warning)
                    .listRowBackground(Color.clear)
            }

            if vm.isLoading && vm.alerts.isEmpty {
                HStack {
                    Spacer()
                    ProgressView("Načítám aktuality…").foregroundStyle(c.muted)
                    Spacer()
                }
                .listRowBackground(Color.clear)
            } else if vm.filtered.isEmpty && vm.errorText == nil {
                ODEmptyState(
                    symbol: "checkmark.circle",
                    title: "Žádné aktuality",
                    message: "Podle zvolených filtrů teď nic nehlásíme."
                )
                .listRowBackground(Color.clear)
            } else {
                Section {
                    ForEach(vm.filtered) { a in
                        NavigationLink {
                            ODAlertDetailView(alert: a)
                        } label: {
                            alertRow(a)
                        }
                    }
                }
                .listRowBackground(c.surface)
            }
        }
        .scrollContentBackground(.hidden)
        .background(c.bg)
        .navigationTitle("Aktuality")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $vm.text, prompt: "Linka, zastávka nebo text")
        .task { await vm.load() }
        .refreshable { await vm.load() }
    }

    private func alertRow(_ a: ODAlert) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(a.title)
                .font(.body.weight(.semibold))
                .foregroundStyle(c.text)
                .fixedSize(horizontal: false, vertical: true)
            if let s = a.summary, !s.isEmpty, s != a.title {
                Text(ODPlainText.strip(s))
                    .font(.subheadline)
                    .foregroundStyle(c.muted)
                    .lineLimit(2)
            }
            HStack(spacing: 6) {
                Text(ODProvider.label(a.provider))
                if let cat = a.categoryLabel { Text("· \(cat)") }
                if let to = a.validTo { Text("· do \(ODTime.dayAndTime(to))") }
            }
            .font(.footnote)
            .foregroundStyle(c.muted)
        }
        .frame(minHeight: 44, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct ODAlertDetailView: View {
    @Environment(\.odColors) private var c
    let alert: ODAlert

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(alert.title)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(c.text)
                    .accessibilityAddTraits(.isHeader)

                HStack(spacing: 6) {
                    tag(ODProvider.label(alert.provider))
                    if let cat = alert.categoryLabel { tag(cat) }
                }

                if alert.validFrom != nil || alert.validTo != nil {
                    VStack(alignment: .leading, spacing: 2) {
                        if let f = alert.validFrom { Text("Od: \(ODTime.dayAndTime(f))") }
                        if let t = alert.validTo { Text("Do: \(ODTime.dayAndTime(t))") }
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(c.text)
                }

                if let d = alert.details, !ODPlainText.strip(d).isEmpty {
                    Text(ODPlainText.strip(d))
                        .font(.body)
                        .foregroundStyle(c.text)
                        .fixedSize(horizontal: false, vertical: true)
                } else if let s = alert.summary, !s.isEmpty {
                    Text(ODPlainText.strip(s))
                        .font(.body)
                        .foregroundStyle(c.text)
                }

                if !alert.lines.isEmpty {
                    sectionTitle("Linky")
                    Text(alert.lines.joined(separator: ", "))
                        .font(.body)
                        .foregroundStyle(c.text)
                }
                if !alert.stops.isEmpty {
                    sectionTitle("Zastávky")
                    Text(alert.stops.prefix(40).joined(separator: ", "))
                        .font(.body)
                        .foregroundStyle(c.text)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(c.bg)
        .navigationTitle("Aktualita")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func tag(_ text: String) -> some View {
        Text(text)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(c.text)
            .padding(.horizontal, 8)
            .frame(minHeight: 26)
            .overlay(RoundedRectangle(cornerRadius: 4, style: .continuous).stroke(c.line, lineWidth: 1))
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.footnote.weight(.bold))
            .foregroundStyle(c.muted)
            .accessibilityAddTraits(.isHeader)
    }
}
