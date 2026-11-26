import MapKit
import SwiftUI

/// Place card in the style of the system Maps app: centered title, one big primary
/// action, live departures from the nearest stops, then a "Podrobnosti" block.
/// Used as a sheet over the map (`asSheet`) and pushed from the list/favourites.
struct DetailView: View {
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var location: LocationService
    @EnvironmentObject private var auth: AuthSession
    @EnvironmentObject private var route: RouteStore
    @Environment(\.dismiss) private var dismiss

    let place: PlaceItem
    var asSheet = false

    // Local vote overrides (server counts arrive via the vote response).
    @State private var displayLikes: Int
    @State private var displayDislikes: Int
    @State private var myVote: Bool?
    @State private var voting = false
    @State private var toast: ToastItem?

    @State private var comments: [WcComment]?
    @State private var stops: [NearbyStop] = []
    @State private var selectedStopId: String?
    @State private var departures: [Departure] = []
    @State private var loadingDepartures = false
    @State private var showPhoto = false
    @State private var departuresOpen = false
    @State private var selectedProvider: String?

    init(place: PlaceItem, asSheet: Bool = false) {
        self.place = place
        self.asSheet = asSheet
        _displayLikes = State(initialValue: place.likes)
        _displayDislikes = State(initialValue: place.dislikes)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                if asSheet { topBar }
                titleBlock
                statusCallout
                noteCallout
                routePicker
                routeNoteView
                navigateButton
                detailsBlock
                if let note = nonBlank(place.accessibilityNote) { accessibilityBlock(note) }
                photoBlock
                if !stops.isEmpty { departuresBlock }
                if !place.isPickup { voteBlock }
                if let comments, !comments.isEmpty { commentsBlock(comments) }
                if !place.isPickup { ContributeBlock(place: place) { toast = $0 } }
            }
            .padding(.horizontal, 16)
            .padding(.top, asSheet ? 14 : 8)
            .padding(.bottom, asSheet ? 28 : 96)
        }
        .background(Color(.systemBackground))
        .safeAreaInset(edge: .bottom) { if !asSheet { bottomToolbar } }
        .navigationTitle(asSheet ? "" : L("Detail"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(asSheet ? .hidden : .visible, for: .navigationBar)
        .task {
            Analytics.track(.placeOpen)
            guard !place.isPickup else { return }
            myVote = VoteMemory.vote(for: place.id)
            comments = (try? await EuroklicAPI().comments(locationId: place.id))?.comments ?? []
        }
        .task(id: "\(place.latitude),\(place.longitude)") {
            let found = await NearbyStopsService.nearby(lat: place.latitude, lon: place.longitude)
            stops = found
            selectedStopId = found.first?.id
        }
        .task(id: selectedStopId) {
            guard let id = selectedStopId else { departures = []; return }
            selectedProvider = nil
            loadingDepartures = true
            departures = await NearbyStopsService.departures(stopId: id)
            loadingDepartures = false
        }
        .task(id: routeTaskId) {
            guard let from = location.lastLocation?.coordinate else { return }
            route.load(key: place.favoriteKey, from: from, to: place.coordinate)
        }
        .onDisappear { route.clear(key: place.favoriteKey) }
        .toast($toast)
    }

    /// Znovu se spočítá, až když poloha poprvé dorazí (ne při každém posunu o pár metrů).
    private var routeTaskId: String { "\(place.favoriteKey)|\(location.lastLocation != nil)" }

    // MARK: - Blocks

    private var topBar: some View {
        HStack {
            if let url = webURL {
                ShareLink(item: url) { circleIcon("square.and.arrow.up") }
                    .accessibilityLabel("Sdílet")
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
            Spacer()
            HStack(spacing: 10) {
                favoriteButton { circleIcon(favorites.isFavorite(place) ? "bookmark.fill" : "bookmark") }
                moreMenu { circleIcon("ellipsis") }
                Button { dismiss() } label: { circleIcon("xmark") }
                    .accessibilityLabel("Zavřít")
            }
        }
    }

    private func favoriteButton<Content: View>(@ViewBuilder label: () -> Content) -> some View {
        Button { favorites.toggle(place) } label: { label() }
            .accessibilityLabel(favorites.isFavorite(place) ? L("Odebrat z oblíbených") : L("Přidat do oblíbených"))
    }

    private func moreMenu<Content: View>(@ViewBuilder label: () -> Content) -> some View {
        Menu {
            if let url = webURL { Link(destination: url) { Label("Otevřít na webu", systemImage: "safari") } }
            if let plan = nonBlank(place.floorPlanUrl), let url = URL(string: plan) {
                Link(destination: url) { Label("Plán stanice (ČD)", systemImage: "map") }
            }
            if let all = URL(string: String(format: "https://app.odjezdy.online/blizko?lat=%.6f&lng=%.6f", place.latitude, place.longitude)) {
                Link(destination: all) { Label("Všechny zastávky v okolí", systemImage: "bus") }
            }
        } label: { label() }
            .accessibilityLabel("Další možnosti")
    }

    private func circleIcon(_ name: String) -> some View {
        Image(systemName: name)
            .font(.body.weight(.semibold))
            .foregroundStyle(.primary)
            .frame(width: 44, height: 44)
            .background(Circle().fill(Color(.secondarySystemFill)))
    }

    private var titleBlock: some View {
        VStack(spacing: 4) {
            Text(place.name)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            if let badge = currentBadge {
                Text(badge)
                    .font(.subheadline)
                    .foregroundStyle(voteBadge(likes: displayLikes, dislikes: displayDislikes, isCd: place.isOfficialSource).color)
                    .multilineTextAlignment(.center)
            }
            if let phone = place.phonePrimary, let url = URL(string: "tel:\(phone.replacingOccurrences(of: " ", with: ""))") {
                Link(destination: url) { Label(phone, systemImage: "phone.fill") }
                    .font(.subheadline)
                    .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// Operating note ("WC closed long-term", key needed, ...) shown right under the title,
    /// because it decides whether the place is usable at all.
    @ViewBuilder
    private var noteCallout: some View {
        if let note = nonBlank(place.note) {
            VStack(alignment: .leading, spacing: 6) {
                Label {
                    Text("Poznámka k místu").font(.subheadline.weight(.semibold))
                } icon: {
                    Image(systemName: "info.circle.fill").foregroundStyle(Theme.warningText)
                }
                Text(note).font(.callout).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
            .accessibilityElement(children: .combine)
        }
    }

    /// Pěšky / MHD: nabídne se jen to, co dává smysl (pěšky do ~45 min, MHD jen když ho mapa zná).
    @ViewBuilder
    private var routePicker: some View {
        if route.options.count > 1 {
            Picker(L("Způsob cesty"), selection: Binding(
                get: { route.selected ?? route.options[0].mode },
                set: { route.select($0) }
            )) {
                ForEach(route.options) { option in
                    Text("\(option.mode.title) · \(option.seconds.routeDuration)").tag(option.mode)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    /// Vysvětlení pod tlačítkem Vyrazit (nahoře by odsunulo tlačítko pod okraj sheetu).
    @ViewBuilder
    private var routeNoteView: some View {
        if let note = routeNote {
            Text(note)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var routeNote: String? {
        switch route.selected {
        case .walking:
            let distance = route.selectedOption?.meters.map { formatDistance($0) } ?? ""
            return L("\(distance) pěšky, orientační trasa. Bezbariérovost cesty není ověřená.")
        case .transit:
            if let far = route.walkingTooFar, let meters = far.meters {
                return L("Pěšky je to příliš daleko (\(formatDistance(meters))). Odhad času s MHD, trasu s přestupy ukážou Mapy. Bezbariérovost není ověřená.")
            }
            return L("Odhad času s MHD, trasu s přestupy ukážou Mapy. Bezbariérovost není ověřená.")
        case nil:
            if let far = route.walkingTooFar, let meters = far.meters {
                return L("Pěšky je to příliš daleko (\(formatDistance(meters))).")
            }
            return nil
        }
    }

    /// Stav místa podle zdroje (např. vyřazené v OpenStreetMap). Jen upozornění, místo se neskrývá.
    @ViewBuilder
    private var statusCallout: some View {
        if let text = nonBlank(place.statusText) {
            VStack(alignment: .leading, spacing: 6) {
                Label {
                    Text("Stav místa").font(.subheadline.weight(.semibold))
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Theme.warningText)
                }
                Text(text).font(.callout).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
            .accessibilityElement(children: .combine)
        }
    }

    private var navigateButton: some View {
        Button(action: openNavigation) {
            Label(navigateLabel, systemImage: route.selected?.symbol ?? "figure.walk")
                .font(.headline)
                .frame(maxWidth: .infinity, minHeight: 52)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.roundedRectangle(radius: 16))
        .tint(Theme.brand)
        .accessibilityLabel(navigateAccessibility)
    }

    private var departuresBlock: some View {
        DisclosureGroup(isExpanded: $departuresOpen) {
            departuresContent.padding(.top, 10)
        } label: {
            Text("Odjezdy").font(.title2.bold())
        }
        .tint(.primary)
    }

    private var departuresContent: some View {
        VStack(alignment: .leading, spacing: 10) {

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(stops) { stop in
                        let on = stop.id == selectedStopId
                        Button { selectedStopId = stop.id } label: {
                            Text(stop.name)
                                .font(.subheadline)
                                .lineLimit(1)
                                .padding(.horizontal, 14)
                                .frame(minHeight: 36)
                                .foregroundStyle(on ? Color.white : Color.primary)
                                .background(Capsule().fill(on ? Theme.brand : Color.clear))
                                .overlay(Capsule().stroke(on ? Color.clear : Color(.separator), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(stop.name), \(formatDistance(Double(stop.distanceM)))")
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
            }

            if providers.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        providerChip(L("Vše"), value: nil)
                        ForEach(providers, id: \.self) { p in
                            providerChip(departures.first(where: { $0.provider == p })?.providerLabel ?? p, value: p)
                        }
                    }
                }
            }

            VStack(spacing: 0) {
                if loadingDepartures && departures.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 64)
                } else if visibleDepartures.isEmpty {
                    Text("Žádné nejbližší odjezdy.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, minHeight: 64)
                } else {
                    ForEach(Array(visibleDepartures.enumerated()), id: \.element.id) { index, d in
                        departureRow(d)
                        if index < visibleDepartures.count - 1 { Divider().padding(.leading, 14) }
                    }
                }
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(.secondarySystemBackground)))

            if let stop = stops.first(where: { $0.id == selectedStopId }),
               let url = URL(string: "https://app.odjezdy.online/zastavka/\(stop.id)") {
                Link(destination: url) {
                    (Text("Odjezdy poskytuje ") + Text("odjezdy.online").foregroundStyle(Theme.tint))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .accessibilityLabel("Odjezdy poskytuje odjezdy.online. Otevřít zastávku \(stop.name).")
            }
        }
    }

    private var providers: [String] {
        var seen = Set<String>()
        return departures.map(\.provider).filter { seen.insert($0).inserted }
    }

    private var visibleDepartures: [Departure] {
        let list = selectedProvider.map { p in departures.filter { $0.provider == p } } ?? departures
        return Array(list.prefix(5))
    }

    private func providerChip(_ title: String, value: String?) -> some View {
        let on = selectedProvider == value
        return Button { selectedProvider = value } label: {
            Text(title)
                .font(.footnote)
                .padding(.horizontal, 12)
                .frame(minHeight: 32)
                .foregroundStyle(on ? Color.white : Color.primary)
                .background(Capsule().fill(on ? Theme.brand : Color(.secondarySystemFill)))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func departureRow(_ d: Departure) -> some View {
        HStack(spacing: 12) {
            if !d.line.isEmpty {
                Text(d.line)
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Theme.brand))
            }
            Text(d.destination.isEmpty ? "—" : d.destination)
                .lineLimit(2)
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 0) {
                Text(Self.timeFormatter.string(from: d.time)).font(.body.monospacedDigit())
                if d.delayMinutes > 0 {
                    Text("+\(d.delayMinutes) min").font(.caption).foregroundStyle(Theme.warningText)
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 52)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var photoBlock: some View {
        if let raw = nonBlank(place.photoUrl), let url = URL(string: raw) {
            Button { showPhoto = true } label: {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .failure:
                        Color(.secondarySystemBackground)
                    default:
                        Color(.secondarySystemBackground).overlay(ProgressView())
                    }
                }
                .frame(height: 190)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .contentShape(RoundedRectangle(cornerRadius: 20))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Fotografie místa")
            .accessibilityHint("Klepnutím zvětšíte")
            .fullScreenCover(isPresented: $showPhoto) { PhotoViewer(url: url) }
        }
    }

    private var detailsBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Podrobnosti").font(.title2.bold())
            VStack(spacing: 0) {
                let rows = detailRows
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    DetailRow(label: row.0, value: row.1)
                    if index < rows.count - 1 { Divider().padding(.leading, 14) }
                }
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(.secondarySystemBackground)))
        }
    }

    private func accessibilityBlock(_ note: String) -> some View {
        DisclosureGroup {
            Text(note).font(.callout).foregroundStyle(.secondary).padding(.top, 6)
        } label: {
            Text("Přístupnost stanice").font(.headline)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color(.secondarySystemBackground)))
    }

    private var voteBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Funguje to?").font(.title2.bold())
            VoteRow(likes: displayLikes, dislikes: displayDislikes, myVote: myVote, voting: voting, onVote: vote)
        }
    }

    private func commentsBlock(_ comments: [WcComment]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Komentáře").font(.title2.bold())
            VStack(spacing: 0) {
                ForEach(Array(comments.enumerated()), id: \.element.id) { index, c in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(c.authorName).font(.subheadline.weight(.semibold))
                            Spacer()
                            if let day = c.dayLabel { Text(day).font(.caption).foregroundStyle(.secondary) }
                        }
                        Text(c.text).font(.callout)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    if index < comments.count - 1 { Divider().padding(.leading, 14) }
                }
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(.secondarySystemBackground)))
        }
    }

    /// Floating pill like Maps (pushed screens only; the sheet keeps these in its top bar).
    private var bottomToolbar: some View {
        HStack(spacing: 28) {
            favoriteButton {
                Image(systemName: favorites.isFavorite(place) ? "bookmark.fill" : "bookmark")
                    .font(.title3)
                    .frame(width: 44, height: 44)
            }
            moreMenu {
                Image(systemName: "ellipsis")
                    .font(.title3)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 4)
        .background(.regularMaterial, in: Capsule())
        .shadow(color: .black.opacity(0.15), radius: 10, y: 3)
        .padding(.bottom, 8)
    }

    // MARK: - Data

    private var subtitle: String {
        let kind = place.isPickup ? L("Výdejní místo Euroklíče") : L("Bezbariérové WC")
        return "\(kind) · \(place.sourceLabel)"
    }

    private var currentStatus: PlaceStatus {
        placeStatus(source: place.source, likes: displayLikes, dislikes: displayDislikes, lastVerified: place.lastVerified)
    }

    private var currentBadge: String? {
        let badge = voteBadge(likes: displayLikes, dislikes: displayDislikes, isCd: place.isOfficialSource)
        return badge == .none ? nil : badge.label(likes: displayLikes, dislikes: displayDislikes)
    }

    private var detailRows: [(String, String)] {
        var rows: [(String, String)] = []
        if let hours = nonBlank(place.openingHours) { rows.append((L("Otevírací doba"), hours)) }
        if place.access == "eurokey" { rows.append((L("Otevírá se"), L("Euroklíčem"))) }
        switch place.wheelchair {
        case "yes": rows.append((L("Budova bezbariérová"), L("Ano")))
        case "no": rows.append((L("Budova bezbariérová"), L("Ne")))
        case "unknown": rows.append((L("Budova bezbariérová"), L("Nevíme")))
        default: break
        }
        if let description = nonBlank(place.description) { rows.append((L("Popis"), prettifyDescription(description))) }
        if let address = nonBlank(place.subtitle) { rows.append((L("Oblast"), address)) }
        if let userLocation = location.lastLocation {
            let d = userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
            rows.append((L("Vzdálenost"), L("\(formatDistance(d)) vzdušnou čarou")))
        }
        if !place.isPickup, let source = nonBlank(place.source) { rows.append((L("Původní zdroj"), originalSourceLabel(source))) }
        if let verified = nonBlank(place.lastVerified) { rows.append((L("Naposledy ověřeno"), String(verified.prefix(10)))) }
        return rows
    }

    private var walkingDistance: CLLocationDistance? {
        guard let userLocation = location.lastLocation else { return nil }
        return userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
    }

    private var navigateLabel: String {
        if let option = route.selectedOption { return L("Vyrazit · \(option.seconds.routeDuration)") }
        return L("Navigovat")
    }

    private var navigateAccessibility: String {
        if let option = route.selectedOption {
            return L("Vyrazit, \(option.mode.title), \(option.seconds.routeDuration)")
        }
        return L("Navigovat k místu")
    }

    private var webURL: URL? {
        guard let raw = nonBlank(place.webUrl) else { return nil }
        if raw.hasPrefix("http") { return URL(string: raw) }
        return URL(string: "https://euroklic.odjezdy.online" + (raw.hasPrefix("/") ? "" : "/") + raw)
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "cs_CZ")
        f.dateFormat = "H:mm"
        return f
    }()

    /// Hands off to the system Maps app (walking directions) — the native route.
    private func openNavigation() {
        Analytics.track(.navigate)
        let item = MKMapItem(placemark: MKPlacemark(coordinate: place.coordinate))
        item.name = place.name
        // A conscious choice wins. Without one: walking for a short hop, otherwise let Maps pick
        // (never force a 5 km walk the app itself would not offer).
        let mode: String
        switch route.selected {
        case .transit: mode = MKLaunchOptionsDirectionsModeTransit
        case .walking: mode = MKLaunchOptionsDirectionsModeWalking
        case nil:
            let tooFar = route.walkingTooFar != nil || (walkingDistance ?? 0) > 3_000
            mode = tooFar ? MKLaunchOptionsDirectionsModeDefault : MKLaunchOptionsDirectionsModeWalking
        }
        item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: mode])
    }

    // MARK: - Vote (CSRF + cookie flow, one 403 retry — mirrors Android)

    private func vote(_ like: Bool) {
        guard !voting, !place.isPickup else { return }
        voting = true
        let previous = myVote
        myVote = like
        Task {
            defer { voting = false }
            do {
                let api = EuroklicAPI()
                var csrf = try await api.csrf().csrf ?? ""
                var response: VoteResponse
                do {
                    response = try await api.vote(id: place.id, type: like ? "like" : "dislike", csrfToken: csrf)
                } catch APIError.http(let code) where code == 403 {
                    // Stale CSRF → refetch once (same contract as Android).
                    csrf = try await api.csrf().csrf ?? ""
                    response = try await api.vote(id: place.id, type: like ? "like" : "dislike", csrfToken: csrf)
                }
                if response.success {
                    Analytics.track(.vote)
                    if let likes = response.likes { displayLikes = likes }
                    if let dislikes = response.dislikes { displayDislikes = dislikes }
                    VoteMemory.save(like, for: place.id)
                    toast = ToastItem(text: like ? L("Díky, zaznamenáno jako funguje.") : L("Díky, zaznamenáno."))
                } else if let message = [response.message, response.error].compactMap(\.self).first(where: { !$0.isEmpty }) {
                    // e.g. "Už jste takto hlasovali." — keep the highlight and remember it
                    VoteMemory.save(like, for: place.id)
                    toast = ToastItem(text: message, kind: .info)
                } else {
                    myVote = previous
                    toast = ToastItem(text: L("Hlas se teď nepodařilo odeslat. Zkuste to znovu."), kind: .failure)
                }
            } catch {
                myVote = previous
                toast = ToastItem(text: (error as? LocalizedError)?.errorDescription ?? L("Hlas se nepodařilo odeslat."), kind: .failure)
            }
        }
    }

    // MARK: - Helpers

    private func nonBlank(_ s: String?) -> String? {
        guard let s, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return s
    }

    /// OSM imports often dump `[Wheelchair: yes]` into description — make it readable.
    private func prettifyDescription(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let pattern = "^\\[?\\s*wheelchair\\s*[:=]\\s*(\\w+)\\s*]?$"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
              let range = Range(match.range(at: 1), in: trimmed)
        else { return raw }
        switch trimmed[range].lowercased() {
        case "yes": return L("Bezbariérový přístup: ano")
        case "limited": return L("Bezbariérový přístup: částečně")
        case "no": return L("Bezbariérový přístup: ne")
        default: return raw
        }
    }

    private func originalSourceLabel(_ source: String) -> String {
        switch source.lowercased() {
        case "cd": return L("ČD (České dráhy)")
        case "osm": return "OpenStreetMap"
        case "mapotic": return "WCkompas (Mapotic)"
        case "user": return L("Uživatelé webu")
        default: return source
        }
    }
}

// MARK: - Small shared pieces

/// Label left, value right — the "Podrobnosti" row of the Maps place card.
struct DetailRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 16) {
            Text(label).foregroundStyle(.secondary)
            Spacer(minLength: 8)
            Text(value).multilineTextAlignment(.trailing)
        }
        .padding(14)
        .accessibilityElement(children: .combine)
    }
}

struct VoteRow: View {
    let likes: Int
    let dislikes: Int
    let myVote: Bool?
    let voting: Bool
    let onVote: (Bool) -> Void

    var body: some View {
        HStack(spacing: 12) {
            voteButton(like: true)
            voteButton(like: false)
        }
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .disabled(voting)
    }

    /// Text + icon (not just an icon and a grey count); the chosen vote is filled, the other stays an outline.
    @ViewBuilder
    private func voteButton(like: Bool) -> some View {
        let selected = myVote == like
        let count = like ? likes : dislikes
        let button = Button { onVote(like) } label: {
            Label {
                Text(like ? "Funguje · \(count)" : "Nefunguje · \(count)")
                    .foregroundStyle(selected ? Color.white : Color.primary)
            } icon: {
                Image(systemName: like ? (selected ? "hand.thumbsup.fill" : "hand.thumbsup")
                    : (selected ? "hand.thumbsdown.fill" : "hand.thumbsdown"))
                    .foregroundStyle(selected ? Color.white : (like ? Theme.success : Theme.warningText))
            }
            .frame(maxWidth: .infinity)
        }
        .tint(like ? Theme.success : Theme.accent)
        .accessibilityLabel(like ? "Hlasovat: funguje. Zatím \(count)." : "Hlasovat: nefunguje. Zatím \(count).")
        .accessibilityAddTraits(selected ? .isSelected : [])
        if selected {
            button.buttonStyle(.borderedProminent)
        } else {
            button.buttonStyle(.bordered)
        }
    }
}
