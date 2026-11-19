import MapKit
import SwiftUI

/// Place card in the style of the system Maps app: centered title, one big primary
/// action, live departures from the nearest stops, then a "Podrobnosti" block.
/// Used as a sheet over the map (`asSheet`) and pushed from the list/favourites.
struct DetailView: View {
    @EnvironmentObject private var favorites: FavoritesStore
    @EnvironmentObject private var location: LocationService
    @EnvironmentObject private var auth: AuthSession
    @Environment(\.dismiss) private var dismiss

    let place: PlaceItem
    var asSheet = false

    // Local vote overrides (server counts arrive via the vote response).
    @State private var displayLikes: Int
    @State private var displayDislikes: Int
    @State private var myVote: Bool?
    @State private var voting = false
    @State private var voteMessage: String?

    @State private var comments: [WcComment]?
    @State private var stops: [NearbyStop] = []
    @State private var selectedStopId: String?
    @State private var departures: [Departure] = []
    @State private var loadingDepartures = false

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
                navigateButton
                if !stops.isEmpty { departuresBlock }
                photoBlock
                detailsBlock
                if let note = nonBlank(place.accessibilityNote) { accessibilityBlock(note) }
                if !place.isPickup { voteBlock }
                if let comments, !comments.isEmpty { commentsBlock(comments) }
            }
            .padding(.horizontal, 16)
            .padding(.top, asSheet ? 14 : 8)
            .padding(.bottom, asSheet ? 28 : 96)
        }
        .background(Color(.systemBackground))
        .safeAreaInset(edge: .bottom) { if !asSheet { bottomToolbar } }
        .navigationTitle(asSheet ? "" : "Detail")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(asSheet ? .hidden : .visible, for: .navigationBar)
        .task {
            guard !place.isPickup else { return }
            comments = (try? await EuroklicAPI().comments(locationId: place.id))?.comments ?? []
        }
        .task(id: "\(place.latitude),\(place.longitude)") {
            let found = await NearbyStopsService.nearby(lat: place.latitude, lon: place.longitude)
            stops = found
            selectedStopId = found.first?.id
        }
        .task(id: selectedStopId) {
            guard let id = selectedStopId else { departures = []; return }
            loadingDepartures = true
            departures = await NearbyStopsService.departures(stopId: id)
            loadingDepartures = false
        }
        .task(id: voteMessage) {
            if voteMessage != nil {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                voteMessage = nil
            }
        }
    }

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
                favoriteButton { circleIcon(favorites.isFavorite(place) ? "star.fill" : "star") }
                moreMenu { circleIcon("ellipsis") }
                Button { dismiss() } label: { circleIcon("xmark") }
                    .accessibilityLabel("Zavřít")
            }
        }
    }

    private func favoriteButton<Content: View>(@ViewBuilder label: () -> Content) -> some View {
        Button { favorites.toggle(place) } label: { label() }
            .accessibilityLabel(favorites.isFavorite(place) ? "Odebrat z oblíbených" : "Přidat do oblíbených")
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
                    .foregroundStyle(currentStatus == .reported ? Theme.accent : Theme.success)
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

    private var navigateButton: some View {
        Button(action: openNavigation) {
            VStack(spacing: 2) {
                Image(systemName: "figure.walk")
                    .font(.title3.weight(.semibold))
                Text(navigateLabel)
                    .font(.headline)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .tint(Theme.brand)
        .controlSize(.large)
        .accessibilityLabel(navigateAccessibility)
    }

    private var departuresBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Odjezdy").font(.title2.bold())

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

            VStack(spacing: 0) {
                if loadingDepartures && departures.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 64)
                } else if departures.isEmpty {
                    Text("Žádné nejbližší odjezdy.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, minHeight: 64)
                } else {
                    ForEach(Array(departures.enumerated()), id: \.element.id) { index, d in
                        departureRow(d)
                        if index < departures.count - 1 { Divider().padding(.leading, 14) }
                    }
                }
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(.secondarySystemBackground)))

            if let stop = stops.first(where: { $0.id == selectedStopId }),
               let url = URL(string: "https://app.odjezdy.online/zastavka/\(stop.id)") {
                (Text("Odjezdy poskytuje ") + Text("odjezdy.online").foregroundColor(Theme.tint))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .onTapGesture { UIApplication.shared.open(url) }
                    .accessibilityAddTraits(.isLink)
                    .accessibilityLabel("Odjezdy poskytuje odjezdy.online. Otevřít zastávku \(stop.name).")
            }
        }
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
                    Text("+\(d.delayMinutes) min").font(.caption).foregroundStyle(Theme.accent)
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
            .accessibilityLabel("Fotografie místa")
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
            if let voteMessage {
                Text(voteMessage).font(.footnote).foregroundStyle(.secondary)
            }
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
            Text("Komentář nebo fotku k místu přidáš na webu.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    /// Floating pill like Maps (pushed screens only; the sheet keeps these in its top bar).
    private var bottomToolbar: some View {
        HStack(spacing: 28) {
            favoriteButton {
                Image(systemName: favorites.isFavorite(place) ? "star.fill" : "star")
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
        let kind = place.isPickup ? "Výdejní místo Euroklíče" : "Bezbariérové WC"
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
        if let hours = nonBlank(place.openingHours) { rows.append(("Otevírací doba", hours)) }
        if place.access == "eurokey" { rows.append(("Otevírá se", "Euroklíčem")) }
        switch place.wheelchair {
        case "yes": rows.append(("Budova bezbariérová", "Ano"))
        case "no": rows.append(("Budova bezbariérová", "Ne"))
        case "unknown": rows.append(("Budova bezbariérová", "Nevíme"))
        default: break
        }
        if let description = nonBlank(place.description) { rows.append(("Popis", prettifyDescription(description))) }
        if let note = nonBlank(place.note) { rows.append(("Poznámka", note)) }
        if let address = nonBlank(place.subtitle) { rows.append(("Oblast", address)) }
        if let userLocation = location.lastLocation {
            let d = userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
            rows.append(("Vzdálenost", "\(formatDistance(d)) vzdušnou čarou"))
        }
        if !place.isPickup, let source = nonBlank(place.source) { rows.append(("Původní zdroj", originalSourceLabel(source))) }
        if let verified = nonBlank(place.lastVerified) { rows.append(("Naposledy ověřeno", String(verified.prefix(10)))) }
        return rows
    }

    private var walkingDistance: CLLocationDistance? {
        guard let userLocation = location.lastLocation else { return nil }
        return userLocation.distance(from: CLLocation(latitude: place.latitude, longitude: place.longitude))
    }

    private var navigateLabel: String {
        guard let d = walkingDistance else { return "Navigovat" }
        return "Navigovat · \(formatWalkingTime(d))"
    }

    private var navigateAccessibility: String {
        guard let d = walkingDistance else { return "Navigovat k místu" }
        return "Navigovat k místu, \(formatDistance(d)), \(formatWalkingTime(d)) pěšky"
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
        let item = MKMapItem(placemark: MKPlacemark(coordinate: place.coordinate))
        item.name = place.name
        item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeWalking])
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
                    if let likes = response.likes { displayLikes = likes }
                    if let dislikes = response.dislikes { displayDislikes = dislikes }
                    voteMessage = like ? "Díky, zaznamenáno jako funguje." : "Díky, zaznamenáno."
                } else if let message = [response.message, response.error].compactMap(\.self).first(where: { !$0.isEmpty }) {
                    // e.g. "Už jste takto hlasovali." — keep the highlight
                    voteMessage = message
                } else {
                    myVote = previous
                    voteMessage = "Hlas se teď nepodařilo odeslat. Zkuste to znovu."
                }
            } catch {
                myVote = previous
                voteMessage = (error as? LocalizedError)?.errorDescription ?? "Hlas se nepodařilo odeslat."
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
        case "yes": return "Bezbariérový přístup: ano"
        case "limited": return "Bezbariérový přístup: částečně"
        case "no": return "Bezbariérový přístup: ne"
        default: return raw
        }
    }

    private func originalSourceLabel(_ source: String) -> String {
        switch source.lowercased() {
        case "cd": return "ČD (České dráhy)"
        case "osm": return "OpenStreetMap"
        case "mapotic": return "WCkompas (Mapotic)"
        case "user": return "Uživatelé webu"
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
            Button { onVote(true) } label: {
                Label("\(likes)", systemImage: myVote == true ? "hand.thumbsup.fill" : "hand.thumbsup")
                    .frame(maxWidth: .infinity)
            }
            .tint(myVote == true ? Theme.success : .secondary)
            .accessibilityLabel("Hlasovat: funguje. Zatím \(likes).")

            Button { onVote(false) } label: {
                Label("\(dislikes)", systemImage: myVote == false ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                    .frame(maxWidth: .infinity)
            }
            .tint(myVote == false ? Theme.accent : .secondary)
            .accessibilityLabel("Hlasovat: nefunguje. Zatím \(dislikes).")
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .controlSize(.large)
        .disabled(voting)
    }
}
